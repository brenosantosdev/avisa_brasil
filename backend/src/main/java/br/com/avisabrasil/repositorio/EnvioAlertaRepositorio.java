package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.EnvioAlerta;
import br.com.avisabrasil.notificacao.Canal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioAlertaRepositorio extends JpaRepository<EnvioAlerta, Long> {

    /** Último envio bem-sucedido do canal, para não repetir o mesmo alerta em sequência. */
    Optional<EnvioAlerta> findFirstByCanalAndStatusOrderByIdDesc(Canal canal, EnvioAlerta.Status status);

    List<EnvioAlerta> findByAlertaIdIn(Collection<Long> alertaIds);

    long deleteByAlertaIdIn(Collection<Long> alertaIds);
}
