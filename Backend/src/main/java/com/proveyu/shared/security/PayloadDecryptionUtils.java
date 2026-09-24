package com.proveyu.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class PayloadDecryptionUtils {

    private static final Logger log = LoggerFactory.getLogger(PayloadDecryptionUtils.class);

    /**
     * Decrypts or decodes password payload sent from frontend.
     * Supports:
     * 1. Base64 encoded payload format (e.g. "BASE64:...") or raw Base64 strings.
     * 2. Raw plain text payload (if sent over HTTPS/TLS).
     */
    public String decryptPassword(String inputPassword) {
        if (inputPassword == null || inputPassword.isBlank()) {
            return inputPassword;
        }

        String trimmed = inputPassword.trim();

        // 1. Check if explicitly prefixed with BASE64:
        if (trimmed.startsWith("BASE64:")) {
            try {
                String encodedPart = trimmed.substring(7);
                byte[] decodedBytes = Base64.getDecoder().decode(encodedPart);
                return new String(decodedBytes, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                log.warn("Failed to decode BASE64 prefixed password payload, falling back to raw payload.");
                return trimmed;
            }
        }

        // 2. Check if the string looks like an encrypted/encoded Base64 string without prefix
        if (isBase64Encoded(trimmed)) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(trimmed);
                String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
                // Verify decoded value is printable UTF-8 text
                if (isPrintableText(decoded)) {
                    return decoded;
                }
            } catch (Exception ignored) {
                // If decoding fails, treat as plain text password
            }
        }

        return trimmed;
    }

    private boolean isBase64Encoded(String str) {
        if (str.length() % 4 != 0 || str.length() < 12) {
            return false;
        }
        return str.matches("^[A-Za-z0-9+/=]+$");
    }

    private boolean isPrintableText(String text) {
        for (char c : text.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }
}
