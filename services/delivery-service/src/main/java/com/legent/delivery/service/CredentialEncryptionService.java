package com.legent.delivery.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive credentials.
 * Uses AES-256 GCM for authenticated encryption.
 */
@Service
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private final SecretKeySpec secretKey;

    public CredentialEncryptionService(
            @Value("${legent.delivery.credential-key}") String credentialKey) {
        if (credentialKey == null || credentialKey.isBlank()) {
            throw new IllegalArgumentException("legent.delivery.credential-key must be configured");
        }
        byte[] keyBytes = deriveKey(credentialKey);
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private byte[] deriveKey(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }

    /**
     * Encrypts a plaintext password and returns encrypted data with IV.
     * Format: base64(iv + ciphertext + authTag)
     */
    public EncryptedCredential encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            String encrypted = Base64.getEncoder().encodeToString(buffer.array());
            String ivString = Base64.getEncoder().encodeToString(iv);

            return new EncryptedCredential(encrypted, ivString);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypts an encrypted credential.
     */
    public String decrypt(String encryptedData, String ivString) {
        if (encryptedData == null || encryptedData.isBlank()) {
            return null;
        }
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] iv = Base64.getDecoder().decode(ivString);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decrypted = cipher.doFinal(encryptedBytes, iv.length, encryptedBytes.length - iv.length);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt credential", e);
        }
    }

    public record EncryptedCredential(String encryptedData, String iv) {}
}
