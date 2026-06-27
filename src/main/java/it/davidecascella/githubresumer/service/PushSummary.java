package it.davidecascella.githubresumer.service;

/**
 * Riassunto di un push prodotto a partire dal diff: un titolo breve e una descrizione più lunga.
 *
 * @param title       una riga, pensata per la notifica (max 150 caratteri).
 * @param description testo più esteso con le modifiche fatte (max 450 caratteri).
 */
public record PushSummary(String title, String description) {

    public static final int MAX_TITLE = 150;
    public static final int MAX_DESCRIPTION = 450;

    /** Tronca difensivamente i due campi entro i limiti, così a valle non sforiamo mai. */
    public PushSummary {
        title = clamp(title, MAX_TITLE);
        description = clamp(description, MAX_DESCRIPTION);
    }

    private static String clamp(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).strip();
    }
}
