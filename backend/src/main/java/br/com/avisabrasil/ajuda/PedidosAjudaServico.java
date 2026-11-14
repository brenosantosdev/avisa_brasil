package br.com.avisabrasil.ajuda;

import br.com.avisabrasil.dominio.PedidoAjuda;
import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de ajuda e "estou bem" (tela web e Telegram). O envio pela web é público, então tem
 * limite por endereço; a fila offline do celular pode reenviar, e o idCliente evita duplicar.
 */
@Service
public class PedidosAjudaServico {

    /** Um pedido novo ou alterado (o painel atualiza ao vivo). */
    public record PedidoAtualizado(Long id) {
    }

    /** Resultado do envio: o pedido e se ele já existia (reenvio da fila offline). */
    public record Registro(PedidoAjuda pedido, boolean jaExistia) {
    }

    /** Muitos envios do mesmo endereço em pouco tempo. */
    public static class LimiteExcedidoException extends RuntimeException {
        public LimiteExcedidoException() {
            super("Muitos envios em pouco tempo. Se for urgente, ligue 193 ou 199.");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PedidosAjudaServico.class);

    static final int LIMITE_POR_ENDERECO = 20;
    static final Duration JANELA_DO_LIMITE = Duration.ofMinutes(10);

    private final PedidoAjudaRepositorio pedidos;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final Duration retencao;
    private final Map<String, Deque<Instant>> enviosPorEndereco = new ConcurrentHashMap<>();

    /**
     * @param retencao por quanto tempo os pedidos ficam guardados (o aviso de privacidade mostra este prazo)
     */
    public PedidosAjudaServico(PedidoAjudaRepositorio pedidos, ApplicationEventPublisher eventos, Clock relogio,
            @Value("${avisabrasil.ajuda.retencao:P90D}") Duration retencao) {
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.relogio = relogio;
        this.retencao = retencao;
    }

    public long diasDeRetencao() {
        return retencao.toDays();
    }

    /** Apaga pedidos e respostas mais antigos que o prazo de retenção (LGPD: guardar só o necessário). */
    @Transactional
    public long apagarAntigos() {
        long apagados = pedidos.deleteByCriadoEmBefore(relogio.instant().minus(retencao));
        if (apagados > 0) {
            log.info("Pedidos de ajuda: {} apagados por passarem de {} dias", apagados, retencao.toDays());
        }
        return apagados;
    }

    /** Apaga todos os pedidos e respostas (só para preparar a apresentação). */
    @Transactional
    public long apagarTodos() {
        long total = pedidos.count();
        pedidos.deleteAllInBatch();
        return total;
    }

    @Transactional
    public Registro registrarPelaWeb(NovoPedidoAjuda novo, String endereco) {
        Optional<PedidoAjuda> existente = pedidos.findByIdCliente(novo.idCliente());
        if (existente.isPresent()) {
            return new Registro(existente.get(), true);
        }
        contarEnvio(endereco);
        Instant agora = relogio.instant();
        PedidoAjuda pedido = new PedidoAjuda(novo.tipo(), PedidoAjuda.Canal.WEB, null, limpar(novo.nome()), agora);
        pedido.informarDadosDaWeb(novo.idCliente(), limpar(novo.telefone()), limpar(novo.mensagem()),
                novo.criadoNoCliente() == null ? null : novo.criadoNoCliente().toInstant());
        if (novo.pessoas() != null) {
            pedido.informarPessoas(novo.pessoas(), agora);
        }
        if (novo.latitude() != null && novo.longitude() != null) {
            pedido.informarLocalizacao(novo.latitude(), novo.longitude(), agora);
        }
        PedidoAjuda salvo = pedidos.save(pedido);
        eventos.publishEvent(new PedidoAtualizado(salvo.getId()));
        return new Registro(salvo, false);
    }

    @Transactional(readOnly = true)
    public List<PedidoAjuda> ultimos() {
        return pedidos.findTop100ByOrderByIdDesc();
    }

    @Transactional
    public Optional<PedidoAjuda> marcarAtendido(long id) {
        return pedidos.findById(id).map(p -> {
            p.marcarAtendido(relogio.instant());
            eventos.publishEvent(new PedidoAtualizado(p.getId()));
            return p;
        });
    }

    private void contarEnvio(String endereco) {
        Instant agora = relogio.instant();
        Deque<Instant> envios = enviosPorEndereco.computeIfAbsent(endereco, e -> new ArrayDeque<>());
        synchronized (envios) {
            while (!envios.isEmpty() && envios.peekFirst().isBefore(agora.minus(JANELA_DO_LIMITE))) {
                envios.pollFirst();
            }
            if (envios.size() >= LIMITE_POR_ENDERECO) {
                throw new LimiteExcedidoException();
            }
            envios.addLast(agora);
        }
    }

    private static String limpar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip();
        return t.isEmpty() ? null : t;
    }
}
