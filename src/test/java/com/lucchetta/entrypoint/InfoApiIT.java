package com.lucchetta.entrypoint;

import com.lucchetta.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InfoApiIT extends IntegrationTest {

    @Test
    void infoIsPublicAndRevealsOnlyTheBuildVersionAndTime() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.build.length()").value(2))
                .andExpect(jsonPath("$.build.version", matchesPattern("\\d+\\.\\d+\\.\\d+")))
                .andExpect(jsonPath("$.build.time").isString());
    }

    @Test
    void webAppOriginCanReadTheInfo() throws Exception {
        mockMvc.perform(get("/actuator/info").header(HttpHeaders.ORIGIN, WEB_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN));

        mockMvc.perform(options("/actuator/info")
                        .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, not(containsString("POST"))));
    }

    @Test
    void otherOriginsAndMethodsAreRejected() throws Exception {
        mockMvc.perform(get("/actuator/info").header(HttpHeaders.ORIGIN, "https://evil.example.test"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/actuator/info")
                        .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
