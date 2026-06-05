package it.davidecascella.githubresumer.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Risposta di POST /v1/messages. Il testo del riassunto è in content[].text. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessageResponse(
        String id,
        String model,
        List<ContentBlock> content
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {
    }

    /** Concatena tutti i blocchi di testo della risposta. */
    public String firstText() {
        if (content == null) {
            return "";
        }
        return content.stream()
                .filter(b -> b.text() != null)
                .map(ContentBlock::text)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
