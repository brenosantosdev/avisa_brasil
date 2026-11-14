package br.com.avisabrasil.web.painel;

import br.com.avisabrasil.ajuda.PedidosAjudaServico;
import br.com.avisabrasil.risco.MotorDeRisco;
import br.com.avisabrasil.risco.SensorServico;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events para o painel: cada navegador conectado recebe na hora o novo nível de risco,
 * as leituras do sensor, os alertas e os pedidos de ajuda. O navegador reconecta sozinho se cair.
 */
@Component
public class EventosDoPainel {

    static final Duration TEMPO_DA_CONEXAO = Duration.ofMinutes(30);

    private final List<SseEmitter> conectados = new CopyOnWriteArrayList<>();

    public SseEmitter conectar() {
        SseEmitter emissor = new SseEmitter(TEMPO_DA_CONEXAO.toMillis());
        conectados.add(emissor);
        emissor.onCompletion(() -> conectados.remove(emissor));
        emissor.onTimeout(() -> conectados.remove(emissor));
        emissor.onError(e -> conectados.remove(emissor));
        enviarPara(emissor, "conectado", Map.of("ok", true));
        return emissor;
    }

    public int conectados() {
        return conectados.size();
    }

    @EventListener
    public void aoAvaliarRisco(MotorDeRisco.RiscoAvaliado evento) {
        enviar("risco", evento.avaliacao());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void aoRegistrarAlerta(MotorDeRisco.AlertaRegistrado evento) {
        var a = evento.alerta();
        enviar("alerta", Map.of("id", a.getId(), "nivel", a.getNivel(), "evento", a.getEvento(),
                "simulado", a.isSimulado(), "mensagem", a.getMensagem()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void aoAtualizarPedido(PedidosAjudaServico.PedidoAtualizado evento) {
        enviar("pedido", Map.of("id", evento.id()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void aoReceberLeitura(SensorServico.NovaLeitura evento) {
        enviar("leitura", Map.of("sensor", evento.codigoSensor(), "nivelCm", evento.nivelCm(),
                "dataHora", evento.dataHora().toString()));
    }

    /** Comentário a cada 25 s para a conexão não ser cortada por inatividade. */
    @Scheduled(fixedRate = 25_000)
    public void manterConexoes() {
        for (SseEmitter emissor : conectados) {
            try {
                emissor.send(SseEmitter.event().comment("ok"));
            } catch (IOException | IllegalStateException e) {
                conectados.remove(emissor);
            }
        }
    }

    private void enviar(String nome, Object dados) {
        conectados.forEach(e -> enviarPara(e, nome, dados));
    }

    private void enviarPara(SseEmitter emissor, String nome, Object dados) {
        try {
            emissor.send(SseEmitter.event().name(nome).data(dados, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            conectados.remove(emissor);
        }
    }
}
