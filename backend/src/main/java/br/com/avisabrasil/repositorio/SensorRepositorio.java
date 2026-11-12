package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.Sensor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorRepositorio extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findByCodigo(String codigo);
}
