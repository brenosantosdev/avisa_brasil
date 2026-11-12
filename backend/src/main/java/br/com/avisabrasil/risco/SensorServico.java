package br.com.avisabrasil.risco;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.dominio.Leitura;
import br.com.avisabrasil.dominio.Sensor;
import br.com.avisabrasil.repositorio.LeituraRepositorio;
import br.com.avisabrasil.repositorio.SensorRepositorio;
import br.com.avisabrasil.rio.Estatistica;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sensor de nível da comunidade: cadastro, leituras e tendência. */
@Service
public class SensorServico {

    /** Leituras usadas para a tendência (regressão linear). */
    static final int LEITURAS_PARA_TENDENCIA = 12;

    /** Uma nova leitura chegou: o motor de risco reavalia na hora. */
    public record NovaLeitura(String codigoSensor) {
    }

    /**
     * Última medição do sensor, se ele estiver ativo.
     *
     * @param velocidadeCmPorDia regressão linear das últimas leituras (pelo horário de cada medição)
     */
    public record Medicao(Sensor sensor, double nivelCm, Double velocidadeCmPorDia, Instant dataHora, Instant recebidoEm) {
    }

    private final SensorRepositorio sensores;
    private final LeituraRepositorio leituras;
    private final AvisaBrasilProperties propriedades;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public SensorServico(SensorRepositorio sensores, LeituraRepositorio leituras, AvisaBrasilProperties propriedades,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.sensores = sensores;
        this.leituras = leituras;
        this.propriedades = propriedades;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** Cria ou atualiza o sensor configurado em application.properties. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cadastrarSensorConfigurado() {
        var c = propriedades.sensor();
        sensores.findByCodigo(c.codigo()).ifPresentOrElse(
                s -> s.atualizar(c.nome(), c.simulado(), c.latitude(), c.longitude(), c.descricao()),
                () -> sensores.save(new Sensor(c.codigo(), c.nome(), c.simulado(), c.latitude(), c.longitude(), c.descricao())));
    }

    /** Sensor configurado; vazio só nos primeiros instantes, antes do cadastro na inicialização. */
    public Optional<Sensor> principal() {
        return sensores.findByCodigo(propriedades.sensor().codigo());
    }

    public Optional<Sensor> porCodigo(String codigo) {
        return sensores.findByCodigo(codigo);
    }

    @Transactional
    public Leitura registrar(Sensor sensor, double nivelCm, OffsetDateTime dataHora) {
        Instant agora = relogio.instant();
        Leitura leitura = leituras.save(new Leitura(sensor, dataHora == null ? agora : dataHora.toInstant(), nivelCm, agora));
        eventos.publishEvent(new NovaLeitura(sensor.getCodigo()));
        return leitura;
    }

    @Transactional(readOnly = true)
    public List<Leitura> ultimas(Sensor sensor, int quantidade) {
        return leituras.findBySensorOrderByIdDesc(sensor, PageRequest.of(0, quantidade));
    }

    /** Medição atual do sensor principal; vazia se não houver leitura recente (sensor parado). */
    @Transactional(readOnly = true)
    public Optional<Medicao> medicaoAtual() {
        Optional<Sensor> configurado = principal();
        if (configurado.isEmpty()) {
            return Optional.empty();
        }
        Sensor sensor = configurado.get();
        List<Leitura> recentes = ultimas(sensor, LEITURAS_PARA_TENDENCIA);
        if (recentes.isEmpty()) {
            return Optional.empty();
        }
        Leitura ultima = recentes.get(0);
        Duration semDados = propriedades.risco().sensorSemDadosApos();
        if (ultima.getRecebidoEm().plus(semDados).isBefore(relogio.instant())) {
            return Optional.empty();
        }
        // só leituras das 48 h anteriores à última (descarta sobras de uma simulação anterior)
        Instant inicio = ultima.getDataHora().minus(Duration.ofHours(48));
        List<Leitura> mesmaSerie = recentes.stream()
                .filter(l -> !l.getDataHora().isAfter(ultima.getDataHora()) && !l.getDataHora().isBefore(inicio))
                .toList();
        return Optional.of(new Medicao(sensor, ultima.getNivelCm(), velocidadeCmPorDia(mesmaSerie),
                ultima.getDataHora(), ultima.getRecebidoEm()));
    }

    /** Regressão linear das leituras (mais recente primeiro), em cm por dia, pelo horário de cada medição. */
    static Double velocidadeCmPorDia(List<Leitura> recentesPrimeiro) {
        if (recentesPrimeiro.size() < 3) {
            return null;
        }
        Instant referencia = recentesPrimeiro.get(recentesPrimeiro.size() - 1).getDataHora();
        double[] x = new double[recentesPrimeiro.size()];
        double[] y = new double[recentesPrimeiro.size()];
        for (int i = 0; i < recentesPrimeiro.size(); i++) {
            Leitura l = recentesPrimeiro.get(recentesPrimeiro.size() - 1 - i);
            x[i] = Duration.between(referencia, l.getDataHora()).toMinutes() / 60.0;
            y[i] = l.getNivelCm();
        }
        if (x[x.length - 1] <= 0) {
            return null;
        }
        return Math.round(Estatistica.inclinacao(x, y) * 24 * 10) / 10.0;
    }
}
