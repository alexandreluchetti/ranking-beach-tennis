package com.beachtennis.ranking.configuration;

import com.beachtennis.ranking.configuration.security.SecurityProperties;
import com.beachtennis.ranking.core.port.PushSender;
import com.beachtennis.ranking.dataprovider.push.DisabledPushSender;
import com.beachtennis.ranking.dataprovider.push.FcmPushSender;
import com.beachtennis.ranking.dataprovider.push.GoogleAccessTokenSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Push is off by default. To switch it on, set {@code PUSH_FCM_ENABLED=true} and
 * {@code PUSH_FCM_CREDENTIALS_FILE} to a Firebase service account key file kept outside the
 * repository.
 */
@Configuration
@EnableConfigurationProperties(PushConfiguration.PushProperties.class)
public class PushConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PushConfiguration.class);

    @ConfigurationProperties(prefix = "app.push.fcm")
    public record PushProperties(boolean enabled, String credentialsFile) {
    }

    @Bean
    PushSender pushSender(PushProperties push, SecurityProperties security) {
        if (!push.enabled()) {
            log.info("Push notifications are disabled (app.push.fcm.enabled=false); in-app notifications only");
            return new DisabledPushSender();
        }
        Assert.hasText(push.credentialsFile(), "app.push.fcm.credentials-file must be set when push is enabled");
        Path credentials = Path.of(push.credentialsFile());
        Assert.isTrue(Files.isReadable(credentials), "Cannot read the FCM credentials file " + credentials);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl("https://fcm.googleapis.com")
                .requestFactory(requestFactory)
                .build();
        log.info("Push notifications enabled through FCM for project {}", security.firebaseProjectId());
        return new FcmPushSender(restClient, security.firebaseProjectId(), new GoogleAccessTokenSupplier(credentials));
    }
}
