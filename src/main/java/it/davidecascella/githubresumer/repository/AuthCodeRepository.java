package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.AuthCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthCodeRepository extends JpaRepository<AuthCode, Long> {

    Optional<AuthCode> findByCodeHash(String codeHash);

    /**
     * Marca il codice come consumato solo se non lo era già: l'UPDATE condizionale è atomico,
     * quindi due richieste concorrenti sullo stesso codice non possono entrambe avere successo.
     * Restituisce il numero di righe aggiornate (1 = consumato ora, 0 = già usato).
     */
    @Modifying
    @Query("update AuthCode a set a.consumedAt = :now where a.id = :id and a.consumedAt is null")
    int markConsumed(@Param("id") Long id, @Param("now") Instant now);

    /** Pulizia opportunistica dei codici scaduti. */
    @Modifying
    @Query("delete from AuthCode a where a.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
