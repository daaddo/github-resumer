package it.davidecascella.githubresumer.repository;

import it.davidecascella.githubresumer.domain.Installation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstallationRepository extends JpaRepository<Installation, Long> {

    Optional<Installation> findByInstallationId(Long installationId);
}
