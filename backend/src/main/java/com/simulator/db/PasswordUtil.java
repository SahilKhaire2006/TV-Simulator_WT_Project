package com.simulator.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Simple password hashing utility using SHA-256 with a per-user salt.
 * <p>
 * For a college project SHA-256 + salt is sufficient. In production you
 * would use BCrypt or Argon2.
 */
public class PasswordUtil {

    private static final String FIXED_SALT = "tv_sim_2024_k3y";

    /**
     * Hash a plain-text password.
     *
     * @param password plain text
     * @return hex-encoded SHA-256 hash
     */
    public static String hash(String password) {
        try {
            String toHash = FIXED_SALT + password;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(toHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    /**
     * Verify a plain-text password against a stored hash.
     *
     * @param password    plain text
     * @param storedHash  hex-encoded hash from the database
     * @return true if they match
     */
    public static boolean verify(String password, String storedHash) {
        return hash(password).equals(storedHash);
    }

    /**
     * Generate a random session token (64 hex chars).
     */
    public static String generateToken() {
        SecureRandom rng = new SecureRandom();
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hash a 4-digit parental PIN (same algorithm, different salt prefix).
     */
    public static String hashPin(String pin) {
        try {
            String toHash = "pin_" + FIXED_SALT + pin;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(toHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash PIN", e);
        }
    }

    public static boolean verifyPin(String pin, String storedHash) {
        return hashPin(pin).equals(storedHash);
    }
}
