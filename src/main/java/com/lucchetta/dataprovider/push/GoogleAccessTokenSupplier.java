package com.lucchetta.dataprovider.push;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * OAuth2 access tokens from a Firebase service account key file. Tokens are cached and
 * refreshed shortly before they expire.
 */
public class GoogleAccessTokenSupplier implements Supplier<String> {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;

    public GoogleAccessTokenSupplier(Path serviceAccountFile) {
        try (InputStream in = Files.newInputStream(serviceAccountFile)) {
            this.credentials = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the Firebase service account file " + serviceAccountFile, e);
        }
    }

    @Override
    public synchronized String get() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot obtain an FCM access token", e);
        }
    }
}
