package br.com.avisabrasil.previsao;

import br.com.avisabrasil.integracoes.Situacao;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Monta a {@link PrevisaoRio}: tendência, pico previsto e comparação com o normal. */
public final class AnaliseRio {

    /** Variação em 7 dias abaixo disso (para mais ou para menos) conta como "estável". */
    static final double LIMITE_ESTAVEL_PERCENTUAL = 5.0;
    /** Variação em 7 dias acima disso conta como mudança rápida. */
    static final double LIMITE_RAPIDO_PERCENTUAL = 20.0;
    static final int DIAS_TENDENCIA = 7;

    private AnaliseRio() {
    }

    /**
     * @param historico pode ser {@code null} quando o histórico ainda não foi obtido
     */
    public static PrevisaoRio analisar(String rio, SerieGlofas serie, SerieHistorica historico,
            Situacao situacaoHistorico, LocalDate hoje) {
        Optional<SerieHistorica> hist = Optional.ofNullable(historico);

        List<PrevisaoRio.Dia> dias = serie.dias().stream().map(d -> {
            Optional<FaixaNormal> normal = hist.flatMap(h -> h.normalPara(d.data()));
            boolean previsao = d.data().isAfter(hoje);
            return new PrevisaoRio.Dia(
                    d.data(),
                    previsao,
                    arredondar(d.valorPrincipal()),
                    previsao ? arredondar(d.p25()) : null,
                    previsao ? arredondar(d.p75()) : null,
                    normal.map(FaixaNormal::p25).map(AnaliseRio::arredondar).orElse(null),
                    normal.map(FaixaNormal::mediana).map(AnaliseRio::arredondar).orElse(null),
                    normal.map(FaixaNormal::p75).map(AnaliseRio::arredondar).orElse(null));
        }).toList();

        Double vazaoHoje = valorEm(serie, hoje);
        Double vazao7 = valorEm(serie, hoje.plusDays(DIAS_TENDENCIA));
        Double variacao = null;
        Tendencia tendencia = null;
        if (vazaoHoje != null && vazao7 != null && vazaoHoje > 0) {
            variacao = Baldes.arredondar((vazao7 - vazaoHoje) / vazaoHoje * 100, 1);
            tendencia = variacao > LIMITE_ESTAVEL_PERCENTUAL ? Tendencia.SUBINDO
                    : variacao < -LIMITE_ESTAVEL_PERCENTUAL ? Tendencia.DESCENDO
                    : Tendencia.ESTAVEL;
        }

        Optional<SerieGlofas.Dia> pico = serie.dias().stream()
                .filter(d -> d.data().isAfter(hoje) && d.valorPrincipal() != null)
                .max(Comparator.comparing(SerieGlofas.Dia::valorPrincipal));

        Optional<FaixaNormal> normalHoje = hist.flatMap(h -> h.normalPara(hoje));
        ComparacaoNormal comparacao = vazaoHoje == null ? null
                : normalHoje.map(n -> n.comparar(vazaoHoje)).orElse(null);

        var resumo = new PrevisaoRio.Resumo(
                arredondar(vazaoHoje),
                vazaoHoje == null ? null : Baldes.porSegundo(vazaoHoje),
                arredondar(vazao7),
                variacao,
                tendencia,
                variacao != null && Math.abs(variacao) > LIMITE_RAPIDO_PERCENTUAL,
                pico.map(SerieGlofas.Dia::data).orElse(null),
                pico.map(SerieGlofas.Dia::valorPrincipal).map(AnaliseRio::arredondar).orElse(null),
                comparacao,
                normalHoje.orElse(null));

        var extremos = new PrevisaoRio.Historico(
                situacaoHistorico,
                hist.flatMap(SerieHistorica::inicio).orElse(null),
                hist.flatMap(SerieHistorica::maximo).map(e -> e.getKey()).orElse(null),
                hist.flatMap(SerieHistorica::maximo).map(e -> arredondar(e.getValue())).orElse(null),
                hist.flatMap(SerieHistorica::minimo).map(e -> e.getKey()).orElse(null),
                hist.flatMap(SerieHistorica::minimo).map(e -> arredondar(e.getValue())).orElse(null));

        return new PrevisaoRio(rio, serie.latitude(), serie.longitude(), hoje, dias, resumo, extremos);
    }

    /** Valor do dia; se faltar, o do dia anterior mais próximo (até 3 dias antes). */
    private static Double valorEm(SerieGlofas serie, LocalDate data) {
        for (int atras = 0; atras <= 3; atras++) {
            Double valor = serie.dia(data.minusDays(atras)).map(SerieGlofas.Dia::valorPrincipal).orElse(null);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private static Double arredondar(Double valor) {
        return valor == null ? null : Baldes.arredondar(valor, 0);
    }
}
