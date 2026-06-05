package it.davidecascella.githubresumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione del client Anthropic (Claude) usato per riassumere i commit.
 *
 * @param apiKey         Chiave API Anthropic.
 * @param model          Modello da usare (default: claude-haiku per riassunti economici/veloci).
 * @param version        Header anthropic-version.
 * @param maxTokens      Limite di token in output del riassunto.
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String model,
        String version,
        Integer maxTokens
) {
    public AnthropicProperties {
        if (model == null || model.isBlank()) {
            model = "claude-haiku-4-5-20251001";
        }
        if (version == null || version.isBlank()) {
            version = "2023-06-01";
        }
        if (maxTokens == null || maxTokens <= 0) {
            maxTokens = 256;
        }
    }
}
