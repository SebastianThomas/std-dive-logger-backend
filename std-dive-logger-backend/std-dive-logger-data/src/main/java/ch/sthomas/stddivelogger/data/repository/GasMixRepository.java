package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.gas.GasMixEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GasMixRepository extends JpaRepository<GasMixEntity, Long> {
    Optional<GasMixEntity> findByO2AndN2AndHe(Double o2, Double n2, Double he);
}
