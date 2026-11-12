package br.com.avisabrasil.risco;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.dominio.EstadoRisco;
import br.com.avisabrasil.previsao.PrevisaoRio;
import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.PrevisaoTempo;
import br.com.avisabrasil.previsao.PrevisaoTempoServico;
import br.com.avisabrasil.previsao.Tendencia;
import br.com.avisabrasil.repositorio.AlertaRepositorio;
import br.com.avisabrasil.repositorio.EstadoRiscoRepositorio;
import br.com.avisabrasil.rio.Nivel;
import br.com.avisabrasil.rio.RioAgora;
import br.com.avisabrasil.rio.RioAgoraServico;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de risco: junta as entradas (régua da ANA, sensor, chuva prevista, vazão prevista),
 * escolhe o maior risco (cheia ou seca) e registra alerta só quando o nível sobe, ou
 * "situação normalizada" quando volta ao Normal.
 */
@Service
public class MotorDeRisco {

    private static final Logger log = LoggerFactory.getLogger(MotorDeRisco.class);

    /** Novo alerta registrado (a etapa 6 envia as notificações a partir deste evento). */
    public record AlertaRegistrado(Alerta alerta, AvaliacaoDeRisco avaliacao) {
    }

    private final RioAgoraServico rioAgora;
    private final SensorServico sensores;
    private final PrevisaoTempoServico tempo;
    private final PrevisaoRioServico glofas;
    private final AlertaRepositorio alertas;
    private final EstadoRiscoRepositorio estados;
    private final ApplicationEventPublisher eventos;
    private final AvisaBrasilProperties.Risco regras;
    private final Clock relogio;
    private volatile AvaliacaoDeRisco ultima;

    public MotorDeRisco(RioAgoraServico rioAgora, SensorServico sensores, PrevisaoTempoServico tempo,
            PrevisaoRioServico glofas, AlertaRepositorio alertas, EstadoRiscoRepositorio estados,
            ApplicationEventPublisher eventos, AvisaBrasilProperties propriedades, Clock relogio) {
        this.rioAgora = rioAgora;
        this.sensores = sensores;
        this.tempo = tempo;
        this.glofas = glofas;
        this.alertas = alertas;
        this.estados = estados;
        this.eventos = eventos;
        this.regras = propriedades.risco();
        this.relogio = relogio;
    }

    /** Última avaliação (avalia agora se ainda não houver). */
    public AvaliacaoDeRisco atual() {
        AvaliacaoDeRisco a = ultima;
        return a != null ? a : avaliar();
    }

    /** Reavalia quando chega leitura nova do sensor. */
    @EventListener
    public void aoReceberLeitura(SensorServico.NovaLeitura evento) {
        avaliar();
    }

    @Transactional
    public synchronized AvaliacaoDeRisco avaliar() {
        RioAgora rio = rioAgora.consultar().dados();
        List<Fator> fatores = new ArrayList<>();

        AvaliacaoMedicao ana = rio == null ? null : rio.avaliacao();
        if (ana != null) {
            fatores.addAll(ana.fatores());
        }

        AvaliacaoMedicao sensor = null;
        Optional<SensorServico.Medicao> medicao = sensores.medicaoAtual();
        if (medicao.isPresent() && rio != null && rio.cotas() != null) {
            var m = medicao.get();
            sensor = RegrasDeRisco.avaliarMedicao(FonteRisco.SENSOR, m.sensor().isSimulado(), m.nivelCm(),
                    m.velocidadeCmPorDia(), rio.normalHoje(), rio.cotas(),
                    regras.horasAntecedenciaCheia(), regras.horasAntecedenciaSeca());
            fatores.addAll(sensor.fatores());
        }

        fatorDeChuva(Optional.ofNullable(tempo.consultar().dados())).ifPresent(fatores::add);
        fatorDoGlofas(Optional.ofNullable(glofas.consultar().dados())).ifPresent(fatores::add);

        Optional<Fator> principal = RegrasDeRisco.principal(fatores);
        var avaliacao = new AvaliacaoDeRisco(
                principal.map(Fator::nivel).orElse(Nivel.NORMAL),
                principal.map(Fator::tipo).orElse(null),
                principal.orElse(null),
                List.copyOf(fatores),
                ana,
                sensor,
                infoDoSensor(medicao),
                rio == null ? null : rio.cotas(),
                OffsetDateTime.now(relogio),
                principal.map(Fator::simulado).orElse(false));
        registrarMudanca(avaliacao);
        ultima = avaliacao;
        return avaliacao;
    }

    /** Alerta só quando o nível sobe (ou muda de cheia para seca); "normalizada" quando volta ao Normal. */
    private void registrarMudanca(AvaliacaoDeRisco a) {
        EstadoRisco estado = estados.findById(EstadoRisco.ID).orElseGet(EstadoRisco::new);
        Nivel antes = estado.getNivel();
        Alerta.Evento evento = null;
        if (a.nivel().maiorQue(antes) || (a.nivel() != Nivel.NORMAL && a.nivel() == antes && a.tipo() != estado.getTipo())) {
            evento = Alerta.Evento.NIVEL_SUBIU;
        } else if (a.nivel() == Nivel.NORMAL && antes != Nivel.NORMAL) {
            evento = Alerta.Evento.NORMALIZADO;
        }
        if (a.nivel() != antes || a.tipo() != estado.getTipo()) {
            estado.mudar(a.nivel(), a.tipo(), relogio.instant());
            estados.save(estado);
        }
        if (evento != null) {
            Alerta alerta = alertas.save(new Alerta(evento, a.nivel(), a.tipo(),
                    a.principal() == null ? null : a.principal().fonte(), a.simulado(),
                    MensagensDeRisco.mensagem(a), relogio.instant()));
            log.info("Risco: {} → {} ({})", antes, a.nivel(), alerta.getMensagem());
            eventos.publishEvent(new AlertaRegistrado(alerta, a));
        }
    }

    @Transactional(readOnly = true)
    public List<Alerta> ultimosAlertas(int quantidade) {
        return alertas.findAllByOrderByIdDesc(PageRequest.of(0, quantidade));
    }

    /** Chuva forte prevista para hoje ou amanhã: Atenção (alagamento e deslizamento). */
    Optional<Fator> fatorDeChuva(Optional<PrevisaoTempo> previsao) {
        LocalDate amanha = LocalDate.now(relogio).plusDays(1);
        return previsao.flatMap(p -> p.dias().stream()
                .filter(d -> !d.data().isAfter(amanha) && d.chuvaMm() >= regras.chuvaForteMm())
                .max((x, y) -> Double.compare(x.chuvaMm(), y.chuvaMm())))
                .map(d -> RegrasDeRisco.fator(FonteRisco.CHUVA, TipoRisco.CHEIA, Nivel.ATENCAO,
                        Fator.Motivo.CHUVA_FORTE, false, true, "mm", d.chuvaMm(), "data", d.data().toString(),
                        "limiteMm", regras.chuvaForteMm()));
    }

    /** O modelo prevê subida rápida (mais de 20% em 7 dias): Atenção preventiva. */
    static Optional<Fator> fatorDoGlofas(Optional<PrevisaoRio> previsao) {
        return previsao.map(PrevisaoRio::resumo)
                .filter(r -> r.tendencia() == Tendencia.SUBINDO && r.mudancaRapida())
                .map(r -> RegrasDeRisco.fator(FonteRisco.GLOFAS, TipoRisco.CHEIA, Nivel.ATENCAO,
                        Fator.Motivo.SUBIDA_RAPIDA_PREVISTA, false, true, "variacao", r.variacaoPercentual7Dias()));
    }

    private AvaliacaoDeRisco.SensorInfo infoDoSensor(Optional<SensorServico.Medicao> medicao) {
        var configurado = sensores.principal();
        if (configurado.isEmpty()) {
            return null;
        }
        var s = configurado.get();
        var zona = relogio.getZone();
        return new AvaliacaoDeRisco.SensorInfo(s.getCodigo(), s.getNome(), s.isSimulado(), medicao.isPresent(),
                medicao.map(SensorServico.Medicao::nivelCm).orElse(null),
                medicao.map(m -> m.dataHora().atZone(zona).toOffsetDateTime()).orElse(null),
                medicao.map(m -> m.recebidoEm().atZone(zona).toOffsetDateTime()).orElse(null));
    }
}
