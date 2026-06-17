package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.domain.User;

/** Utente identificato dal flusso OAuth + il suo access token GitHub (user-to-server) in chiaro. */
public record AuthenticatedGitHubUser(User user, String accessToken) {
}
