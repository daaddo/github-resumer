package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca atomica: vince una sola rotazione/logout concorrente (0 righe = già revocato). */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id = :id and r.revokedAt is null")
    int markRevoked(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.rotatedTo = :rotatedTo where r.id = :id")
    int setRotatedTo(@Param("id") Long id, @Param("rotatedTo") Long rotatedTo);

    /** Brucia tutti i token attivi dell'utente (logout-ovunque / furto rilevato). */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
