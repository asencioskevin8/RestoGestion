package com.restogestion.repository;

import com.restogestion.entity.SesionCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SesionCajaRepository extends JpaRepository<SesionCaja, Long> {
    Optional<SesionCaja> findFirstByEstadoOrderByFechaAperturaDesc(String estado);
}
