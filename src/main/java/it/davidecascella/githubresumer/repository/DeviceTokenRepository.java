package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByUserId(Long userId);

    /** Rimuove un token diventato invalido (device disinstallato/token scaduto). Idempotente. */
    @Modifying
    @Query("delete from DeviceToken d where d.fcmToken = :fcmToken")
    int deleteByFcmToken(@Param("fcmToken") String fcmToken);

    /** Rimuove tutti i dispositivi dell'utente (logout globale: niente più push su nessun device). */
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
