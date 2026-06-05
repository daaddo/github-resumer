package it.davidecascella.githubresumer.service;

import java.util.List;

/** Astrazione del riassunto dei commit, così il provider LLM è intercambiabile. */
public interface SummarizationService {

    /**
     * Produce un riassunto compatto (1-2 frasi) adatto a una notifica su smartwatch.
     *
     * @param repoFullName    es. "torvalds/linux"
     * @param branch          ramo del push (es. "main")
     * @param commitMessages  messaggi dei commit del push
     * @return testo breve del riassunto
     */
    String summarizePush(String repoFullName, String branch, List<String> commitMessages);
}
