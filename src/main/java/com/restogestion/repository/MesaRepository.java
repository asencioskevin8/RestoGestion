package com.restogestion.repository;

import com.restogestion.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {
    Optional<Mesa> findByNumeroMesa(String numeroMesa);
    List<Mesa> findByEstado(String estado);
    List<Mesa> findAllByOrderByNumeroMesaAsc();
}
