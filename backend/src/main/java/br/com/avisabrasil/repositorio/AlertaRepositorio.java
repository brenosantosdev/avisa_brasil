package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.Alerta;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaRepositorio extends JpaRepository<Alerta, Long> {

    List<Alerta> findAllByOrderByIdDesc(Pageable pagina);

    List<Alerta> findBySimuladoTrue();
}
