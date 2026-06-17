# GitHub Resumer — Backend (implementazione)

Backend Spring Boot che fa da ponte tra i Webhook della **GitHub App** e le notifiche push **FCM**
verso l'app Android/Wear OS, riassumendo i commit con **Claude**.

## Architettura

```
Android ──login (PKCE)──▶ /auth/github/login ──▶ GitHub OAuth ──▶ /auth/github/callback
                                                                    │ upsert User + codice usa-e-getta
                                                                    ▼
                            App Link  https://api.resumer.davidecascella.it/app/callback?code=<one-time>
                                                                    │
Android ──code + code_verifier──▶ POST /auth/exchange ──▶ access token (JWT) + refresh token

Android ──Bearer JWT──▶ /api/me/fcm-token            (registra token FCM)
Android ──Bearer JWT──▶ /api/me/tracked-repos        (CRUD repo tracciati)

GitHub ──push webhook──▶ /webhooks/github
        verifica firma HMAC ▶ 200 OK subito ▶ [async] filtro "Buttafuori"
        ▶ riassunto Claude ▶ FCM data-message ▶ smartwatch
```

### Componenti
- **`domain/`** — entità JPA `User`, `TrackedRepository`.
- **`repository/`** — Spring Data JPA.
- **`client/`** — client Feign: `GitHubOAuthClient`, `GitHubApiClient`, `AnthropicClient`.
- **`service/`** — `GitHubOAuthService`, `GitHubAppService`, `JwtService`, `SummarizationService`
  (impl. `ClaudeSummarizationService`), `FcmService`, `WebhookService`, `WebhookSignatureVerifier`, `UserService`.
- **`web/`** — `AuthController`, `WebhookController`, `MeController` + interceptor JWT su `/api/**`.
- **`db/migration/`** — Flyway `V1__init.sql`.

### Scelte chiave
- **Regola del Buttafuori**: il webhook risponde sempre `200` (tranne firma non valida → `401`, perché
  non proviene da GitHub) e delega il lavoro a un metodo `@Async`, così non va mai in timeout.
- **Filtro repo**: un push viene notificato solo se esiste un `TrackedRepository` con quel `repo_full_name`.
- **FCM data-only**: niente blocco `notification`, così è il client a decidere come mostrarlo sul watch.
- **Resilienza**: se Claude o Firebase non sono configurati/falliscono, c'è sempre un fallback (riassunto
  grezzo dal primo commit / log-only) e il flusso non si interrompe.

## Setup

1. **Segreti locali**: copia il template e riempi i valori
   ```
   src/main/resources/application-local.properties.template  ->  application-local.properties
   ```
   (il file `application-local.properties` è git-ignored).

2. **MySQL**: l'URL di default è `jdbc:mysql://localhost:3306/githubresumer?createDatabaseIfNotExist=true`.
   Imposta `DB_USERNAME` / `DB_PASSWORD` nel file locale. Flyway crea le tabelle al primo avvio.

3. **GitHub App** (già registrata): nel file locale metti `github.app-id`, `github.client-id`,
   `github.client-secret`, `github.webhook-secret` e `github.private-key` (in **PKCS#8** — vedi comando
   `openssl` nel template). Imposta il **Webhook URL** della App su `<callback-base-url>/webhooks/github`
   e l'evento **Push**. Imposta la **Callback URL** OAuth su `<callback-base-url>/auth/github/callback`.
   Usa l'URL pubblico del backend (`https://api.resumer.davidecascella.it`) come `github.callback-base-url`.

4. **Anthropic**: metti `anthropic.api-key` nel file locale (TODO: ancora da creare la chiave).

5. **Firebase/FCM**: per ora `firebase.enabled=false` (le notifiche vengono loggate). Quando avrai il
   progetto Firebase: scarica il service account JSON, imposta `firebase.enabled=true` e
   `firebase.service-account-path=file:./firebase-service-account.json`.

6. **Avvio**:
   ```
   ./mvnw spring-boot:run
   ```

## Endpoint

| Metodo | Path | Auth | Descrizione |
|--------|------|------|-------------|
| GET | `/auth/github/login` | no | Avvia il login OAuth (PKCE): redirect a GitHub |
| GET | `/auth/github/callback` | no | Callback OAuth e ritorno post-install → pagina HTML che rimbalza all'app (App Link) col codice usa-e-getta |
| POST | `/auth/exchange` | no | Scambia il codice usa-e-getta (+ code_verifier PKCE) con access token + refresh token |
| POST | `/auth/refresh` | no | Ruota il refresh token ed emette un nuovo access token |
| POST | `/auth/logout` | no | Revoca il refresh token |
| POST | `/webhooks/github` | firma HMAC | Riceve gli eventi della GitHub App (push, installation, installation_repositories) |
| PUT | `/api/me/fcm-token` | Bearer JWT | Registra/aggiorna il token FCM |
| GET | `/api/me/tracked-repos` | Bearer JWT | Lista repo tracciati |
| POST | `/api/me/tracked-repos` | Bearer JWT | Aggiunge un repo (`{"repoFullName":"owner/repo"}`) |
| DELETE | `/api/me/tracked-repos` | Bearer JWT | Rimuove un repo |

## TODO lato frontend (Android/Wear OS) — da fare tu

Per integrare l'app col backend:
1. **Client Retrofit verso il backend** (nuova base URL, non più solo `api.github.com`).
2. **Login OAuth (PKCE)**: apri `/auth/github/login` in una Custom Tab; gestisci l'App Link
   `https://api.resumer.davidecascella.it/app/callback?code=<one-time>` (intent-filter `autoVerify`
   nel Manifest + assetlinks.json) e scambia il codice via `POST /auth/exchange` (con `code_verifier`)
   per ottenere access + refresh token, da salvare cifrati in DataStore.
3. **FCM**: aggiungi Firebase all'app (`google-services.json`), ottieni il token con
   `FirebaseMessaging.getInstance().token` e fai `PUT /api/me/fcm-token` con header `Authorization: Bearer <JWT>`.
4. **Repo tracciati**: sostituisci il salvataggio locale dei repo con le chiamate a `/api/me/tracked-repos`.

> Nota: l'invio del token FCM dal telefono al watch (`DataLayerListenerService`, TODO già presente)
> e il salvataggio nel DataStore del watch restano da completare.
