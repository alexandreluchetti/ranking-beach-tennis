package com.beachtennis.ranking.dataprovider.push;

import com.beachtennis.ranking.core.port.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sends push messages through the Firebase Cloud Messaging HTTP v1 API. The same message
 * reaches Android directly and iOS through APNs (which additionally requires an APNs key
 * configured in the Firebase project).
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final RestClient restClient;
    private final String projectId;
    private final Supplier<String> accessToken;

    /**
     * @param restClient  client with base URL {@code https://fcm.googleapis.com}
     * @param accessToken supplies a valid OAuth2 access token with the firebase.messaging scope
     */
    public FcmPushSender(RestClient restClient, String projectId, Supplier<String> accessToken) {
        this.restClient = restClient;
        this.projectId = projectId;
        this.accessToken = accessToken;
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        Map<String, Object> message = Map.of("message", Map.of(
                "token", deviceToken,
                "notification", Map.of("title", title, "body", body),
                "data", data,
                "android", Map.of("priority", "HIGH")));
        return restClient.post()
                .uri("/v1/projects/{projectId}/messages:send", projectId)
                .header("Authorization", "Bearer " + accessToken.get())
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .exchange((request, response) -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return Result.SENT;
                    }
                    String error = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (response.getStatusCode().value() == 404 || error.contains("\"UNREGISTERED\"")) {
                        return Result.INVALID_TOKEN;
                    }
                    log.warn("FCM rejected a push message: HTTP {} {}", response.getStatusCode().value(), error);
                    return Result.FAILED;
                });
    }
}
