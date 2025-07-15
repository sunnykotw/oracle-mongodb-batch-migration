package com.example.migration.repository.oracle;

import com.example.migration.model.entity.OracleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OracleRepository extends JpaRepository<OracleEntity, Long> {
    Optional<OracleEntity> findTop1ByOrderByIdAsc();
}