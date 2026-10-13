package com.lucchetta.dataprovider.push;

import com.lucchetta.core.port.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contract with the FCM HTTP v1 API, against a mocked server (no real FCM call). */
class FcmPushSenderTest {

    private static final String URL = "https://fcm.googleapis.com/v1/projects/ranking-beach-tennis/messages:send";

    private MockRestServiceServer server;
    private FcmPushSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fcm.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new FcmPushSender(builder.build(), "ranking-beach-tennis", () -> "access-token");
    }

    @Test
    void sendsNotificationWithDataAndBearerToken() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(jsonPath("$.message.token").value("device-1"))
                .andExpect(jsonPath("$.message.notification.title").value("Partida registrada"))
                .andExpect(jsonPath("$.message.notification.body").value("corpo"))
                .andExpect(jsonPath("$.message.data.matchId").value("m-1"))
                .andRespond(withSuccess("{\"name\": \"projects/x/messages/1\"}", MediaType.APPLICATION_JSON));

        assertThat(sender.send("device-1", "Partida registrada", "corpo", Map.of("matchId", "m-1")))
                .isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void unregisteredTokenIsReportedAsInvalid() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"status\": \"NOT_FOUND\", \"details\": [{\"errorCode\": \"UNREGISTERED\"}]}}"));

        assertThat(sender.send("gone", "t", "b", Map.of())).isEqualTo(PushSender.Result.INVALID_TOKEN);
    }

    @Test
    void otherErrorsAreFailuresNotInvalidTokens() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(sender.send("device-1", "t", "b", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }
}
