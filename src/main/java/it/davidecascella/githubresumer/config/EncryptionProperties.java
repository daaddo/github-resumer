package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chiave AES-256 per la cifratura dei token GitHub a riposo nel DB.
 * Genera con: openssl rand -base64 32
 */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String secretKey) {
}
