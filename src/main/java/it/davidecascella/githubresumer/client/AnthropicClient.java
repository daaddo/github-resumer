package it.davidecascella.githubresumer.client;

import it.davidecascella.githubresumer.client.dto.AnthropicMessageRequest;
import it.davidecascella.githubresumer.client.dto.AnthropicMessageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Client per la Messages API di Anthropic (Claude). */
@FeignClient(name = "anthropicClient", url = "https://api.anthropic.com")
public interface AnthropicClient {

    @PostMapping(value = "/v1/messages", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    AnthropicMessageResponse createMessage(
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader("anthropic-version") String version,
            @RequestBody AnthropicMessageRequest request
    );
}
