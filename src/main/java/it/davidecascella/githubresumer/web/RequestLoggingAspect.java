package it.davidecascella.githubresumer.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Logga a livello INFO ogni richiesta che arriva ai controller (@RestController): metodo HTTP,
 * endpoint, query string e body. I valori sensibili (token, segreti, code, signature, ecc.) vengono
 * mascherati con "***" sia nel body (per chiave) sia nei query param. I body grezzi (byte[], es. il
 * payload del webhook) non vengono stampati.
 */
@Slf4j
@Aspect
@Component
public class RequestLoggingAspect {

    /** Chiavi (normalizzate: minuscole, senza '_') il cui valore non va mai loggato. */
    private static final Set<String> SENSITIVE = Set.of(
            "token", "accesstoken", "refreshtoken", "githubaccesstoken", "fcmtoken",
            "secret", "clientsecret", "code", "codeverifier", "codechallenge",
            "state", "signature", "authorization", "password", "privatekey", "key", "bearer");

    private final ObjectMapper objectMapper;

    public RequestLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Before("@within(org.springframework.web.bind.annotation.RestController)")
    public void logIncoming(JoinPoint joinPoint) {
        String method = "?";
        String uri = joinPoint.getSignature().toShortString();

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                uri = uri + "?" + maskQuery(query);
            }
        }

        String body = extractBody(joinPoint);
        if (body != null) {
            log.info("➜ {} {} body={}", method, uri, body);
        } else {
            log.info("➜ {} {}", method, uri);
        }
    }

    /** Serializza l'argomento @RequestBody mascherando i campi sensibili; null se non c'è un body. */
    private String extractBody(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] params = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < params.length && i < args.length; i++) {
            if (!params[i].isAnnotationPresent(RequestBody.class)) {
                continue;
            }
            Object arg = args[i];
            if (arg == null) {
                return "null";
            }
            if (arg instanceof byte[] bytes) {
                return "<" + bytes.length + " bytes>"; // es. payload webhook grezzo: non lo stampiamo
            }
            try {
                Object converted = objectMapper.convertValue(arg, Object.class);
                return objectMapper.writeValueAsString(mask(converted));
            } catch (Exception e) {
                return "<non serializzabile>";
            }
        }
        return null;
    }

    /** Maschera ricorsivamente i valori delle chiavi sensibili in mappe/liste. */
    private Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                out.put(key, isSensitive(key) ? "***" : mask(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::mask).collect(Collectors.toList());
        }
        return value;
    }

    private String maskQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(pair -> {
                    int eq = pair.indexOf('=');
                    if (eq < 0) {
                        return pair;
                    }
                    String key = pair.substring(0, eq);
                    return isSensitive(key) ? key + "=***" : pair;
                })
                .collect(Collectors.joining("&"));
    }

    private boolean isSensitive(String key) {
        return SENSITIVE.contains(key.toLowerCase().replace("_", ""));
    }
}
