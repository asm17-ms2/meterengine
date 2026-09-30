package com.meterengine.payment.entity;

import com.meterengine.payment.config.PaymentProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor.CipherAlgorithm;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

@Component
@Converter
public class BillingKeyConverter implements AttributeConverter<String, String> {

  private static final String VERSION_PREFIX = "v1:";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;

  private final AesBytesEncryptor encryptor;

  public BillingKeyConverter(PaymentProperties properties) {
    byte[] key = Base64.getDecoder().decode(properties.billingKeyEncryptionKey());
    if (key.length != KEY_BYTES) {
      throw new IllegalArgumentException("billing key encryption key must be 32 bytes");
    }
    this.encryptor =
        new AesBytesEncryptor(
            new SecretKeySpec(key, "AES"),
            KeyGenerators.secureRandom(IV_BYTES),
            CipherAlgorithm.GCM);
  }

  @Override
  public String convertToDatabaseColumn(String billingKey) {
    byte[] encrypted = encryptor.encrypt(billingKey.getBytes(StandardCharsets.UTF_8));
    return VERSION_PREFIX + Base64.getEncoder().encodeToString(encrypted);
  }

  @Override
  public String convertToEntityAttribute(String stored) {
    if (!stored.startsWith(VERSION_PREFIX)) {
      throw new IllegalStateException("unknown billing key cipher version");
    }
    try {
      byte[] decrypted =
          encryptor.decrypt(Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length())));
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("billing key cannot be decrypted", exception);
    }
  }
}
