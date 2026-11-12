package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.CacheDeFonte;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.integracoes.Situacao;
import br.com.avisabrasil.integracoes.ana.AnaClient;
import br.com.avisabrasil.previsao.ComparacaoNormal;
import br.com.avisabrasil.previsao.FaixaNormal;
import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.SerieHistorica;
import br.com.avisabrasil.risco.AvaliacaoMedicao;
import br.com.avisabrasil.risco.FonteRisco;
import br.com.avisabrasil.risco.RegrasDeRisco;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Monta "O rio agora": telemetria da ANA (a cada 15 min), cotas de referência e histórico. */
@Service
public class RioAgoraServico {

    static final int EVENTOS_NO_HISTORICO = 5;
    static final int MINIMO_ANOS_NORMAL = 10;

    private final AnaClient ana;
    private final HistoricoAnaServico historico;
    private final PrevisaoRioServico glofas;
    private final AvisaBrasilProperties.Ana config;
    private final AvisaBrasilProperties.Risco risco;
    private final Clock relogio;
    private final CacheDeFonte<List<LeituraRio>> telemetria;

    public RioAgoraServico(AnaClient ana, HistoricoAnaServico historico, PrevisaoRioServico glofas,
            AvisaBrasilProperties propriedades, Clock relogio) {
        this.ana = ana;
        this.historico = historico;
        this.glofas = glofas;
        this.config = propriedades.ana();
        this.risco = propriedades.risco();
        this.relogio = relogio;
        this.telemetria = new CacheDeFonte<>(
                "ANA telemetria " + config.principal().codigo(),
                config.intervaloTelemetria(),
                propriedades.fontes().novaTentativa(),
                propriedades.fontes().pastaCache().resolve("ana-telemetria-" + config.principal().codigo() + ".json"),
                RioAgoraServico::converterTelemetria,
                relogio);
    }

    private static List<LeituraRio> converterTelemetria(String json) {
        List<LeituraRio> leituras = AnaConversor.telemetria(json);
        if (leituras.isEmpty()) {
            throw new IllegalArgumentException("ANA sem leituras telemétricas no período");
        }
        return leituras;
    }

    public void atualizarSeNecessario() {
        if (ana.habilitado() && telemetria.deveAtualizar()) {
            atualizar();
        }
    }

    public void atualizar() {
        telemetria.atualizar(() -> ana.buscarTelemetria(config.principal().codigo(), LocalDate.now(relogio)));
    }

    public RespostaComFonte<RioAgora> consultar() {
        LocalDate hoje = LocalDate.now(relogio);
        SerieHistorica seriePrincipal = historico.serie(config.principal().codigo());
        ExtremosAnuais extremosPrincipal = new ExtremosAnuais(seriePrincipal.valores());
        Optional<CotasDeReferencia> cotas = CotasDeReferencia.definir(config.cotas(), extremosPrincipal);
        // com poucos anos baixados ainda não dá para falar em "normal"
        Optional<FaixaNormal> normal = seriePrincipal.normalPara(hoje)
                .filter(n -> n.anoFinal() - n.anoInicial() + 1 >= MINIMO_ANOS_NORMAL);
        ComparacaoNormal doGlofas = Optional.ofNullable(glofas.consultar().dados())
                .map(d -> d.resumo().comparacaoHoje()).orElse(null);

        List<LeituraRio> leituras = telemetria.valor().orElse(List.of());
        RioAgora.Leitura atual = null;
        Double variacao24h = null;
        Double velocidade = null;
        AvaliacaoMedicao avaliacao = null;
        ComparacaoNormal daAna = null;
        if (!leituras.isEmpty()) {
            LeituraRio ultima = leituras.get(leituras.size() - 1);
            atual = new RioAgora.Leitura(ultima.dataHora(), ultima.cotaCm(), ultima.vazaoM3s());
            variacao24h = variacaoEm24h(leituras);
            velocidade = velocidadeCmPorDia(leituras);
            if (cotas.isPresent()) {
                avaliacao = RegrasDeRisco.avaliarMedicao(FonteRisco.ANA, false, ultima.cotaCm(), velocidade,
                        normal.orElse(null), cotas.get(), risco.horasAntecedenciaCheia(), risco.horasAntecedenciaSeca());
            }
            daAna = normal.map(n -> n.comparar(ultima.cotaCm())).orElse(null);
        }

        var dados = new RioAgora(
                config.principal(),
                atual,
                variacao24h,
                velocidade,
                porHora(leituras),
                cotas.orElse(null),
                avaliacao,
                normal.orElse(null),
                daAna,
                doGlofas,
                divergem(daAna, doGlofas),
                montarHistorico(config.principal(), seriePrincipal, extremosPrincipal),
                montarHistorico(config.cidade(), historico.serie(config.cidade().codigo()), null));

        String erro = ana.habilitado() ? telemetria.ultimoErro().orElse(null)
                : "Integração com a ANA desligada: defina ANA_USUARIO e ANA_SENHA";
        return new RespostaComFonte<>(
                AnaClient.FONTE,
                AnaClient.FONTE_URL,
                telemetria.obtidoEm().map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                ana.habilitado() ? telemetria.situacao() : (leituras.isEmpty() ? Situacao.INDISPONIVEL : Situacao.DESATUALIZADO),
                erro,
                dados);
    }

    /** Seca, normal ou cheia segundo cada fonte; divergem quando apontam lados diferentes. */
    static boolean divergem(ComparacaoNormal ana, ComparacaoNormal glofas) {
        return ana != null && glofas != null && lado(ana) != lado(glofas);
    }

    private static int lado(ComparacaoNormal c) {
        return switch (c) {
            case MUITO_ABAIXO, ABAIXO -> -1;
            case NORMAL -> 0;
            case ACIMA, MUITO_ACIMA -> 1;
        };
    }

    private RioAgora.Historico montarHistorico(AvisaBrasilProperties.Estacao estacao, SerieHistorica serie,
            ExtremosAnuais extremos) {
        ExtremosAnuais e = extremos != null ? extremos : new ExtremosAnuais(serie.valores());
        var valores = serie.valores();
        return new RioAgora.Historico(
                estacao,
                historico.progresso(estacao),
                e.anosCompletos(),
                valores.isEmpty() ? null : valores.firstKey(),
                valores.isEmpty() ? null : valores.lastKey(),
                e.maioresCheias(EVENTOS_NO_HISTORICO),
                e.maioresSecas(EVENTOS_NO_HISTORICO));
    }

    /** Diferença entre a última leitura e a leitura mais próxima de 24 horas antes. */
    static Double variacaoEm24h(List<LeituraRio> leituras) {
        LeituraRio ultima = leituras.get(leituras.size() - 1);
        LocalDateTime alvo = ultima.dataHora().minusHours(24);
        return leituras.stream()
                .filter(l -> !l.dataHora().isAfter(alvo))
                .reduce((a, b) -> b)
                .filter(l -> Duration.between(l.dataHora(), alvo).toHours() < 3)
                .map(l -> ultima.cotaCm() - l.cotaCm())
                .orElse(null);
    }

    /** Regressão linear das leituras das últimas 24 horas, em cm por dia. */
    static Double velocidadeCmPorDia(List<LeituraRio> leituras) {
        LocalDateTime fim = leituras.get(leituras.size() - 1).dataHora();
        List<LeituraRio> janela = leituras.stream().filter(l -> l.dataHora().isAfter(fim.minusHours(24))).toList();
        if (janela.size() < 6) {
            return null;
        }
        double[] x = new double[janela.size()];
        double[] y = new double[janela.size()];
        for (int i = 0; i < janela.size(); i++) {
            x[i] = Duration.between(janela.get(0).dataHora(), janela.get(i).dataHora()).toMinutes() / 60.0;
            y[i] = janela.get(i).cotaCm();
        }
        return Math.round(Estatistica.inclinacao(x, y) * 24 * 10) / 10.0;
    }

    /** Uma leitura por hora (a última de cada hora), para o gráfico. */
    static List<RioAgora.Ponto> porHora(List<LeituraRio> leituras) {
        Map<LocalDateTime, LeituraRio> porHora = new LinkedHashMap<>();
        leituras.forEach(l -> porHora.put(l.dataHora().withMinute(0).withSecond(0).withNano(0), l));
        List<RioAgora.Ponto> pontos = new ArrayList<>();
        porHora.values().forEach(l -> pontos.add(new RioAgora.Ponto(l.dataHora(), l.cotaCm())));
        return pontos;
    }
}
