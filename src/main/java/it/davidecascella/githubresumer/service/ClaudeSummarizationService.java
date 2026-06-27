package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.client.AnthropicClient;
import it.davidecascella.githubresumer.client.dto.AnthropicMessageRequest;
import it.davidecascella.githubresumer.client.dto.AnthropicMessageResponse;
import it.davidecascella.githubresumer.config.AnthropicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Riassunto di un push tramite Claude (Anthropic Messages API), a partire dal DIFF delle modifiche.
 * Chiede al modello un JSON con "title" (max 150) e "description" (max 450).
 * In caso di errore, chiave assente o diff vuoto fa fallback su un riassunto "grezzo" basato sui
 * messaggi di commit, così il flusso di notifica non si interrompe mai.
 */
@Slf4j
@Service
public class ClaudeSummarizationService implements SummarizationService {

    private static final String SYSTEM_PROMPT = """
            Sei un assistente che descrive l'attività di sviluppo a partire dal diff di un push.
            Ricevi un diff unificato (git) con le modifiche EFFETTIVE ai file. Analizza COSA è
            cambiato nel codice: non ti vengono forniti i messaggi di commit e non devi inventarli.
            Rispondi ESCLUSIVAMENTE con un oggetto JSON valido (nessun markdown, nessun testo fuori
            dal JSON) con esattamente due campi:
              - "title": una sola riga in italiano, massimo 150 caratteri, che riassume le modifiche.
              - "description": testo in italiano, massimo 450 caratteri, che descrive le modifiche fatte.
            Niente emoji, niente markdown, nessun campo extra.
            """;

    /**
     * Tetto ai caratteri di diff inviati al modello: i diff possono essere enormi e i token
     * costano. Tagliamo qui per tenere sotto controllo costo e latenza.
     */
    private static final int MAX_DIFF_CHARS = 50_000;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    public ClaudeSummarizationService(AnthropicClient anthropicClient,
                                      AnthropicProperties properties,
                                      ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PushSummary summarizePush(String repoFullName, String branch, String diff,
                                     List<String> commitMessagesForFallback) {
        if (diff == null || diff.isBlank()) {
            log.debug("Diff assente per {}: uso il fallback.", repoFullName);
            return fallback(repoFullName, commitMessagesForFallback);
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("anthropic.api-key assente: uso il fallback senza LLM.");
            return fallback(repoFullName, commitMessagesForFallback);
        }
        try {
            String userContent = buildPrompt(repoFullName, branch, diff);
            AnthropicMessageRequest request = AnthropicMessageRequest.userPrompt(
                    properties.model(), properties.maxTokens(), SYSTEM_PROMPT, userContent);

            AnthropicMessageResponse response =
                    anthropicClient.createMessage(properties.apiKey(), properties.version(), request);

            PushSummary parsed = parse(response.firstText());
            return parsed != null ? parsed : fallback(repoFullName, commitMessagesForFallback);
        } catch (Exception e) {
            log.error("Riassunto Claude fallito per {}: {}", repoFullName, e.getMessage());
            return fallback(repoFullName, commitMessagesForFallback);
        }
    }

    private String buildPrompt(String repoFullName, String branch, String diff) {
        String safeDiff = diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n... [diff troncato]"
                : diff;
        return "Repository: " + repoFullName + '\n'
                + "Branch: " + (branch == null ? "?" : branch) + '\n'
                + "Diff unificato delle modifiche del push:\n" + safeDiff;
    }

    /**
     * Estrae title/description dalla risposta del modello. Tollerante: cerca l'oggetto JSON anche
     * se il modello lo avvolge in testo o code fence. Ritorna null se non riesce a estrarre nulla
     * di utile (così il chiamante può fare fallback).
     */
    private PushSummary parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            String title = node.path("title").asString("");
            String description = node.path("description").asString("");
            if (title.isBlank() && description.isBlank()) {
                return null;
            }
            return new PushSummary(title, description);
        } catch (Exception e) {
            log.warn("JSON del riassunto non parsabile: {}", e.getMessage());
            return null;
        }
    }

    /** Riassunto grezzo senza LLM: titolo = primo commit (+N), descrizione = elenco compatto. */
    private PushSummary fallback(String repoFullName, List<String> commitMessages) {
        if (commitMessages == null || commitMessages.isEmpty()) {
            return new PushSummary("Nuovo push su " + repoFullName, "");
        }
        String first = firstLine(commitMessages.get(0));
        int extra = commitMessages.size() - 1;
        String title = extra > 0 ? first + " (+" + extra + ")" : first;

        StringBuilder description = new StringBuilder();
        for (String message : commitMessages) {
            String line = firstLine(message);
            if (!line.isBlank()) {
                description.append("- ").append(line).append('\n');
            }
        }
        return new PushSummary(title, description.toString());
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.split("\n", 2)[0];
    }
}
