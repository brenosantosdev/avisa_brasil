package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.PontoSeguro;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PontoSeguroRepositorio extends JpaRepository<PontoSeguro, Long> {

    List<PontoSeguro> findByAtivoTrueOrderByNomeAsc();

    Optional<PontoSeguro> findByIdAndAtivoTrue(Long id);
}
