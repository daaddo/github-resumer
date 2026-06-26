# Guida Architetturale e di Sviluppo Backend - Progetto: "GitHub Resumer"

## 1. Panoramica del Progetto
Il backend di "GitHub Resumer" fa da ponte intelligente tra GitHub e un'applicazione Android (collegata a uno smartwatch Wear OS). Il suo compito è ricevere i Webhook da un'installazione "GitHub App", filtrare i repository di interesse dell'utente, riassumere i commit tramite un LLM e inviare notifiche push silenziose e compatte tramite Firebase Cloud Messaging (FCM).

**Stack Tecnologico:**
* **Framework:** Spring Boot (Java/Kotlin)
* **Database:** MySQL
* **Integrazioni:** GitHub App (Webhook + OAuth), API LLM (es. OpenAI), Firebase Admin SDK (FCM).

---

## 2. Regole Globali (Critiche)


### 2.2. Regola d'Oro dei Webhook (Il "Buttafuori")
GitHub invierà eventi per *tutti* i repository dell'utente. Il server deve filtrare solo quelli tracciati.
* **Risposta Obbligatoria:** L'endpoint del Webhook DEVE sempre restituire `HTTP 200 OK` (ResponseEntity.ok), anche se il repository viene scartato o l'evento viene ignorato. Restituire errori (4xx/5xx) o andare in timeout causerà la sospensione del Webhook da parte di GitHub.

---

## 3. Modello Dati (Database Schema Concept)

Implementare le entità JPA necessarie per supportare questa struttura:

* **User:**
    * `id` (PK)
    * `github_username` (Unique)
    * `github_account_id` (ID univoco di GitHub)
* **DeviceToken:** (un record per dispositivo: supporto multi-device alle notifiche push)
    * `id` (PK)
    * `user_id` (FK su User)
    * `fcm_token` (Unique — token per inviare la notifica push al device Android)
* **TrackedRepository:**
    * `id` (PK)
    * `user_id` (FK su User)
    * `repo_full_name` (es. "username/nome-repo")
    * *Nota: L'utente riceverà notifiche solo per i record presenti in questa tabella.*

---

## 4. Flussi Principali da Implementare

### Flusso A: Autenticazione e Setup (OAuth + App Installation)
1.  Esporre endpoint per l'accesso OAuth di GitHub per identificare l'utente.
2.  Ricevere il token FCM dal client Android e salvarlo come `DeviceToken` dell'utente (uno per dispositivo).