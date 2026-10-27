package com.meterengine.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private static final String VERSION_PREFIX = "v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final Set<String> DEVELOPMENT_PROFILES = Set.of("local", "test");
  private static final List<byte[]> DEVELOPMENT_KEYS =
      List.of(decodeKey(CryptoProperties.LOCAL_KEY), decodeKey(CryptoProperties.TEST_KEY));

  private final SecretKey key;
  private final SecureRandom secureRandom = new SecureRandom();

  public EncryptedStringConverter(CryptoProperties properties, Environment environment) {
    byte[] keyBytes = decodeKey(properties.encryptionKey());
    if (isDevelopmentKey(keyBytes) && !hasDevelopmentProfile(environment)) {
      throw new IllegalStateException(
          "development encryption key is allowed only with local or test profile");
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public String convertToDatabaseColumn(String plain) {
    if (plain == null) {
      return null;
    }
    byte[] iv = new byte[IV_BYTES];
    secureRandom.nextBytes(iv);
    byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, iv, plain.getBytes(StandardCharsets.UTF_8));
    byte[] stored = ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
    return VERSION_PREFIX + Base64.getEncoder().encodeToString(stored);
  }

  @Override
  public String convertToEntityAttribute(String stored) {
    if (stored == null) {
      return null;
    }
    if (!stored.startsWith(VERSION_PREFIX)) {
      throw new IllegalStateException("unknown cipher version");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("stored cipher text is not base64", exception);
    }
    if (decoded.length < IV_BYTES + TAG_BITS / Byte.SIZE) {
      throw new IllegalStateException("stored cipher text is too short");
    }
    byte[] iv = Arrays.copyOfRange(decoded, 0, IV_BYTES);
    byte[] encrypted = Arrays.copyOfRange(decoded, IV_BYTES, decoded.length);
    return new String(cipher(Cipher.DECRYPT_MODE, iv, encrypted), StandardCharsets.UTF_8);
  }

  private byte[] cipher(int mode, byte[] iv, byte[] input) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          mode == Cipher.ENCRYPT_MODE ? "encryption failed" : "decryption failed", exception);
    }
  }

  private static byte[] decodeKey(String encryptionKey) {
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(encryptionKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("encryption key is not base64");
    }
    if (keyBytes.length != KEY_BYTES) {
      throw new IllegalStateException("encryption key must be 32 bytes");
    }
    return keyBytes;
  }

  private static boolean isDevelopmentKey(byte[] keyBytes) {
    return DEVELOPMENT_KEYS.stream()
        .anyMatch(developmentKey -> Arrays.equals(developmentKey, keyBytes));
  }

  private static boolean hasDevelopmentProfile(Environment environment) {
    return Arrays.stream(environment.getActiveProfiles()).anyMatch(DEVELOPMENT_PROFILES::contains);
  }
}
