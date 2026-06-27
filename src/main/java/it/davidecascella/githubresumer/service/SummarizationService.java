package it.davidecascella.githubresumer.service;

import java.util.List;

/** Astrazione del riassunto di un push, così il provider LLM è intercambiabile. */
public interface SummarizationService {

    /**
     * Produce un titolo e una descrizione delle modifiche, analizzando il diff del push
     * (non i messaggi di commit). Non deve mai lanciare eccezioni: in caso di problemi
     * fa fallback su un riassunto grezzo basato sui messaggi di commit.
     *
     * @param repoFullName             es. "torvalds/linux"
     * @param branch                   ramo del push (es. "main")
     * @param diff                     diff unificato del push (può essere vuoto se non recuperabile)
     * @param commitMessagesForFallback messaggi dei commit, usati SOLO per il fallback senza LLM
     * @return titolo (max 150) e descrizione (max 450)
     */
    PushSummary summarizePush(String repoFullName, String branch, String diff,
                              List<String> commitMessagesForFallback);
}
