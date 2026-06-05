package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Corpo della richiesta a POST /v1/messages. */
public record AnthropicMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages
) {
    public record Message(String role, String content) {
    }

    public static AnthropicMessageRequest userPrompt(String model, int maxTokens, String system, String userContent) {
        return new AnthropicMessageRequest(model, maxTokens, system, List.of(new Message("user", userContent)));
    }
}
