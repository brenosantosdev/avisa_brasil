package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.Leitura;
import br.com.avisabrasil.dominio.Sensor;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeituraRepositorio extends JpaRepository<Leitura, Long> {

    /** Últimas leituras na ordem em que chegaram (a mais recente primeiro). */
    List<Leitura> findBySensorOrderByIdDesc(Sensor sensor, Pageable pagina);
}
