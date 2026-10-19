package com.lucchetta.entrypoint.rest.me;

/**
 * @param displayName how the user wants to be called (nickname); null leaves it unchanged
 * @param fullName    first and last name (D-024); null leaves it unchanged
 */
public record UpdateMeRequest(String displayName, String fullName) {
}
