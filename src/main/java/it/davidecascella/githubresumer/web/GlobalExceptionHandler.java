package it.davidecascella.githubresumer.web;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request  ) {
        log.info("Metodo non valido chiamato a: ",request.getRequestURI());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Richiesta non valida");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Richiesta non valida";
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", message));
    }

    /**
     * Errori provenienti dalle chiamate alle API di GitHub (client Feign).
     * Logghiamo status, metodo, URL e corpo della risposta di GitHub per la diagnosi,
     * ma verso il client esponiamo un 500 generico (non propaghiamo lo status di GitHub).
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, String>> handleFeign(FeignException ex) {
        log.error("Chiamata a GitHub fallita: status={} method={} url={} body={}",
                ex.status(),
                ex.request() != null ? ex.request().httpMethod() : "n/d",
                ex.request() != null ? ex.request().url() : "n/d",
                ex.contentUTF8(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Errore nella comunicazione con GitHub"));
    }

    /**
     * Path inesistente (nessun handler / nessuna risorsa statica): risponde 404, non 500.
     * Per il browser (Accept text/html) mostra la pagina "Niente trovato" con link alla dashboard
     * admin; per i client API restituisce un 404 JSON coerente con il resto delle risposte.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ModelAndView handleNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Path non trovato: {}", request.getRequestURI());
        if (prefersHtml(request)) {
            return new ModelAndView("error/404", HttpStatus.NOT_FOUND);
        }
        // Client API: 404 JSON scritto direttamente (response già gestita -> ritorno null).
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Risorsa non trovata\"}");
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Errore non gestito", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Errore interno"));
    }

    /** True se il client preferisce HTML (navigazione da browser) rispetto a JSON. */
    private static boolean prefersHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
