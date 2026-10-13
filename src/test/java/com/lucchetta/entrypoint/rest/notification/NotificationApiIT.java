package com.lucchetta.entrypoint.rest.notification;

import com.lucchetta.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationApiIT extends IntegrationTest {

    private String ana;
    private String beto;
    private String caio;
    private String dani;
    private String matchBody;

    @BeforeEach
    void registerPlayersAndAdmin() throws Exception {
        playerId(ADMIN_UID, "Admin");
        ana = playerId("uid-ana", "Ana");
        beto = playerId("uid-beto", "Beto");
        caio = playerId("uid-caio", "Caio");
        dani = playerId("uid-dani", "Dani");
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        mockMvc.perform(post("/api/admin/seasons").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Primavera\", \"startDate\": \"%s\", \"endDate\": \"%s\"}"
                                .formatted(today.minusDays(30), today.plusDays(30))))
                .andExpect(status().isCreated());
        matchBody = """
                {"id": "%s", "playedAt": "%s", "format": "STANDARD", "teamAGames": 6, "teamBGames": 4,
                 "teamA": [{"playerId": "%s"}, {"playerId": "%s"}],
                 "teamB": [{"playerId": "%s"}, {"playerId": "%s"}]}
                """.formatted(UUID.randomUUID(),
                ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).minusHours(1).toOffsetDateTime(),
                ana, beto, caio, dani);
    }

    @Test
    void registrationNotifiesParticipantsAndAdministratorExactlyOnce() throws Exception {
        String matchId = registerAsAna();
        mockMvc.perform(post("/api/matches").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON).content(matchBody))
                .andExpect(status().isOk()); // idempotent resend

        mockMvc.perform(get("/api/notifications").with(token("uid-caio", "Caio", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_REGISTERED"))
                .andExpect(jsonPath("$.items[0].matchId").value(matchId))
                .andExpect(jsonPath("$.items[0].title").value("Partida registrada"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mockMvc.perform(get("/api/notifications").with(adminToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(5);
    }

    @Test
    void notificationsAreReadOnlyByTheirRecipient() throws Exception {
        registerAsAna();
        String caioNotification = JsonPath.read(mockMvc.perform(get("/api/notifications")
                        .with(token("uid-caio", "Caio", null)))
                .andReturn().getResponse().getContentAsString(), "$.items[0].id");

        mockMvc.perform(post("/api/notifications/" + caioNotification + "/read").with(token("uid-ana", "Ana", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(post("/api/notifications/" + caioNotification + "/read").with(token("uid-caio", "Caio", null)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications").with(token("uid-caio", "Caio", null)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));

        mockMvc.perform(post("/api/notifications/read-all").with(token("uid-beto", "Beto", null)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications").with(token("uid-beto", "Beto", null)))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void voidNotifiesWithReason() throws Exception {
        String matchId = registerAsAna();

        mockMvc.perform(post("/api/admin/matches/" + matchId + "/void").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Jogo de treino\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").with(token("uid-beto", "Beto", null)))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_VOIDED"))
                .andExpect(jsonPath("$.items[0].body").value(containsString(
                        "Motivo: Jogo de treino. Seus pontos nesta partida: 9 → 0.")));
    }

    @Test
    void devicesCanBeRegisteredAndRemoved() throws Exception {
        mockMvc.perform(put("/api/me/devices").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"fcm:token-123\", \"platform\": \"ANDROID\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/me/devices").with(token("uid-beto", "Beto", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"fcm:token-123\", \"platform\": \"ANDROID\"}"))
                .andExpect(status().isNoContent()); // same phone, someone else signed in
        assertThat(jdbcTemplate.queryForObject("select player_id::text from device_token", String.class))
                .isEqualTo(beto);

        mockMvc.perform(put("/api/me/devices").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\": \" \", \"platform\": \"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE"));
        mockMvc.perform(delete("/api/me/devices/fcm:token-123").with(token("uid-beto", "Beto", null)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from device_token", Integer.class)).isZero();
    }

    private String registerAsAna() throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/matches").with(token("uid-ana", "Ana", null))
                        .contentType(MediaType.APPLICATION_JSON).content(matchBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String playerId(String uid, String name) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/me").with(token(uid, name, null)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
