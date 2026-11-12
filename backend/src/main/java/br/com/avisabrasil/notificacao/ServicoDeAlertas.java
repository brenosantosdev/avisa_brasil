package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.dominio.EnvioAlerta;
import br.com.avisabrasil.repositorio.EnvioAlertaRepositorio;
import br.com.avisabrasil.risco.AvaliacaoDeRisco;
import br.com.avisabrasil.risco.MotorDeRisco;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia cada alerta do motor de risco por todos os canais ligados (log, ntfy, Telegram, SMS).
 *
 * <ul>
 *   <li>Um alerta nunca é enviado duas vezes pelo mesmo canal.</li>
 *   <li>Se o conteúdo (evento, nível, tipo, simulado) for igual ao último alerta enviado pelo canal,
 *       não reenvia em sequência (fica registrado como IGNORADO_REPETIDO).</li>
 *   <li>O envio acontece depois de gravar o alerta e fora da requisição (assíncrono).</li>
 * </ul>
 */
@Service
public class ServicoDeAlertas {

    private static final Logger log = LoggerFactory.getLogger(ServicoDeAlertas.class);

    /** Situação de um canal, para o painel. */
    public record SituacaoCanal(Canal canal, boolean ligado, String motivoDesligado) {
    }

    /** Resultado do envio de teste. */
    public record ResultadoTeste(Canal canal, boolean ok, String erro) {
    }

    private final List<Notificador> notificadores;
    private final MontadorDeMensagem montador;
    private final EnvioAlertaRepositorio envios;
    private final Clock relogio;

    public ServicoDeAlertas(List<Notificador> notificadores, MontadorDeMensagem montador,
            EnvioAlertaRepositorio envios, Clock relogio) {
        this.notificadores = notificadores.stream().sorted(Comparator.comparing(Notificador::canal)).toList();
        this.montador = montador;
        this.envios = envios;
        this.relogio = relogio;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void informarCanais() {
        canais().forEach(c -> log.info("Alertas: canal {} {}", c.canal(),
                c.ligado() ? "ligado" : "desligado (" + c.motivoDesligado() + ")"));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void aoRegistrarAlerta(MotorDeRisco.AlertaRegistrado evento) {
        enviar(evento.alerta(), evento.avaliacao());
    }

    /** Envia o alerta por todos os canais ligados e registra o resultado de cada um. */
    public List<EnvioAlerta> enviar(Alerta alerta, AvaliacaoDeRisco avaliacao) {
        MensagemAlerta mensagem = montador.montar(alerta, avaliacao);
        List<EnvioAlerta> resultado = new ArrayList<>();
        for (Notificador n : notificadores) {
            if (!n.habilitado()) {
                continue;
            }
            EnvioAlerta.Status status;
            String erro = null;
            if (repetido(n.canal(), mensagem.assinatura())) {
                status = EnvioAlerta.Status.IGNORADO_REPETIDO;
            } else {
                try {
                    n.enviar(mensagem);
                    status = EnvioAlerta.Status.ENVIADO;
                } catch (RuntimeException e) {
                    status = EnvioAlerta.Status.FALHOU;
                    // as implementações já devolvem mensagens sem segredos; por garantia, limita o tamanho
                    erro = Optional.ofNullable(e.getMessage()).map(m -> m.length() > 290 ? m.substring(0, 290) : m)
                            .orElse(e.getClass().getSimpleName());
                    log.warn("Alertas: {} não enviou o alerta {} ({})", n.canal(), alerta.getId(), erro);
                }
            }
            resultado.add(envios.save(new EnvioAlerta(alerta.getId(), n.canal(), status, mensagem.assinatura(), erro,
                    relogio.instant())));
        }
        return resultado;
    }

    /** O último alerta enviado com sucesso por este canal tem o mesmo conteúdo? */
    boolean repetido(Canal canal, String assinatura) {
        return envios.findFirstByCanalAndStatusOrderByIdDesc(canal, EnvioAlerta.Status.ENVIADO)
                .map(e -> e.getAssinatura().equals(assinatura))
                .orElse(false);
    }

    /** Mensagem de teste por todos os canais ligados (não é alerta e não entra no histórico). */
    public List<ResultadoTeste> enviarTeste() {
        MensagemAlerta teste = montador.teste();
        List<ResultadoTeste> resultados = new ArrayList<>();
        for (Notificador n : notificadores) {
            if (!n.habilitado()) {
                continue;
            }
            try {
                n.enviar(teste);
                resultados.add(new ResultadoTeste(n.canal(), true, null));
            } catch (RuntimeException e) {
                resultados.add(new ResultadoTeste(n.canal(), false, e.getMessage()));
            }
        }
        return resultados;
    }

    public List<SituacaoCanal> canais() {
        return notificadores.stream().map(n -> new SituacaoCanal(n.canal(), n.habilitado(), n.motivoDesligado())).toList();
    }
}
