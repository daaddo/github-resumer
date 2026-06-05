package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.AnthropicClient;
import it.davidecascella.githubresumer.client.dto.AnthropicMessageRequest;
import it.davidecascella.githubresumer.client.dto.AnthropicMessageResponse;
import it.davidecascella.githubresumer.config.AnthropicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Riassunto dei commit tramite Claude (Anthropic Messages API).
 * In caso di errore o chiave assente fa fallback su un riassunto "grezzo" basato sui messaggi,
 * così il flusso di notifica non si interrompe mai.
 */
@Slf4j
@Service
public class ClaudeSummarizationService implements SummarizationService {

    private static final String SYSTEM_PROMPT = """
            Sei un assistente che riassume l'attività di sviluppo per una notifica su smartwatch.
            Dati i messaggi di commit di un push, produci UNA sola frase in italiano, max 120 caratteri,
            che descriva cosa è stato fatto. Niente elenchi, niente markdown, niente emoji, nessun a capo.
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties properties;

    public ClaudeSummarizationService(AnthropicClient anthropicClient, AnthropicProperties properties) {
        this.anthropicClient = anthropicClient;
        this.properties = properties;
    }

    @Override
    public String summarizePush(String repoFullName, String branch, List<String> commitMessages) {
        if (commitMessages == null || commitMessages.isEmpty()) {
            return "Nuovo push su " + repoFullName;
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("anthropic.api-key assente: uso il fallback senza LLM.");
            return fallback(repoFullName, commitMessages);
        }
        try {
            String userContent = buildPrompt(repoFullName, branch, commitMessages);
            AnthropicMessageRequest request = AnthropicMessageRequest.userPrompt(
                    properties.model(), properties.maxTokens(), SYSTEM_PROMPT, userContent);

            AnthropicMessageResponse response =
                    anthropicClient.createMessage(properties.apiKey(), properties.version(), request);

            String text = response.firstText().trim();
            return text.isBlank() ? fallback(repoFullName, commitMessages) : text;
        } catch (Exception e) {
            log.error("Riassunto Claude fallito per {}: {}", repoFullName, e.getMessage());
            return fallback(repoFullName, commitMessages);
        }
    }

    private String buildPrompt(String repoFullName, String branch, List<String> commitMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(repoFullName).append('\n');
        sb.append("Branch: ").append(branch == null ? "?" : branch).append('\n');
        sb.append("Messaggi di commit:\n");
        for (String message : commitMessages) {
            // Solo la prima riga di ogni commit, per restare compatti.
            String firstLine = message == null ? "" : message.split("\n", 2)[0];
            sb.append("- ").append(firstLine).append('\n');
        }
        return sb.toString();
    }

    private String fallback(String repoFullName, List<String> commitMessages) {
        String first = commitMessages.get(0).split("\n", 2)[0];
        int extra = commitMessages.size() - 1;
        String suffix = extra > 0 ? " (+" + extra + ")" : "";
        return first + suffix;
    }
}
