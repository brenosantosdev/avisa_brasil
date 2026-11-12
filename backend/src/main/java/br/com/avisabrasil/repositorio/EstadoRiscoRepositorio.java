package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.EstadoRisco;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoRiscoRepositorio extends JpaRepository<EstadoRisco, Long> {
}
