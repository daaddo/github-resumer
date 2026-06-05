package it.davidecascella.githubresumer.web;

import it.davidecascella.githubresumer.service.WebhookService;
import it.davidecascella.githubresumer.service.WebhookSignatureVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint dei webhook della GitHub App.
 *
 * Regola del Buttafuori: salvo firma non valida (caller non legittimo), rispondiamo SEMPRE 200 OK
 * e deleghiamo il lavoro pesante in modo asincrono, così GitHub non sospende il webhook.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/github")
public class WebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookService webhookService;

    public WebhookController(WebhookSignatureVerifier signatureVerifier, WebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        // Firma assente/errata = chiamata non proveniente da GitHub: rifiutiamo (non causa sospensione).
        if (!signatureVerifier.isValid(rawBody, signature)) {
            log.warn("Webhook con firma non valida: respinto.");
            return ResponseEntity.status(401).build();
        }

        if ("push".equals(event)) {
            webhookService.handlePushAsync(rawBody);
        } else {
            // ping e ogni altro evento: accettati e ignorati.
            log.debug("Evento webhook '{}' ignorato.", event);
        }
        return ResponseEntity.ok().build();
    }
}
