package br.com.avisabrasil.repositorio;

import br.com.avisabrasil.dominio.PedidoAjuda;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoAjudaRepositorio extends JpaRepository<PedidoAjuda, Long> {

    /** Último pedido de ajuda da mesma conversa, para juntar localização e número de pessoas. */
    Optional<PedidoAjuda> findFirstByCanalAndContatoAndTipoAndCriadoEmAfterOrderByIdDesc(
            PedidoAjuda.Canal canal, String contato, PedidoAjuda.Tipo tipo, Instant depoisDe);

    List<PedidoAjuda> findTop100ByOrderByIdDesc();
}
