package com.finmates.admin.service;

import com.finmates.admin.config.KeycloakAdminProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for Keycloak admin operations in fm-admin.
 * Handles password reset and other admin user management tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminKeycloakService {

    private final KeycloakAdminProvider keycloakProvider;

    /**
     * Changes a user's password in Keycloak.
     * Called by admin to force-reset a user's password.
     *
     * @param keycloakId The Keycloak user UUID
     * @param newPassword The new password (must be at least 8 chars with uppercase, lowercase, digit)
     * @throws ResponseStatusException BAD_REQUEST if password validation fails
     * @throws ResponseStatusException INTERNAL_SERVER_ERROR if Keycloak operation fails
     */
    public void changeUserPassword(String keycloakId, String newPassword) {
        log.info("Admin changing password for Keycloak user: {}", keycloakId);

        // Validate keycloakId
        if (!StringUtils.hasText(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak ID is required");
        }

        // Validate password
        if (!StringUtils.hasText(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be blank");
        }

        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }

        if (!isValidPassword(newPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
            );
        }

        try {
            // Create credential representation
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            // Reset password via Keycloak admin API
            keycloakProvider.newKeycloakAdminClient()
                    .realm("finmates")  // Target realm for the operation
                    .users()
                    .get(keycloakId)
                    .resetPassword(credential);

            log.info("Password successfully changed for Keycloak user: {}", keycloakId);

        } catch (Exception e) {
            log.error("Failed to change password for user {}: {}", keycloakId, e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to change user password. Please try again later."
            );
        }
    }

    /**
     * Validates password strength.
     * Password must contain at least one uppercase, one lowercase, and one digit.
     */
    private boolean isValidPassword(String password) {
        // At least 8 chars, 1 uppercase, 1 lowercase, 1 digit
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        return password.matches(passwordRegex);
    }
}
