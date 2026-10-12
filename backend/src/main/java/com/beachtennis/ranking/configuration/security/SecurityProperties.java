package com.beachtennis.ranking.configuration.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param firebaseProjectId Firebase project ID (not the project number); tokens must be issued
 *                          for this project
 * @param adminSubject      authentication subject (Firebase UID) of the single administrator;
 *                          blank means no administrator
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String firebaseProjectId, String adminSubject) {
}
