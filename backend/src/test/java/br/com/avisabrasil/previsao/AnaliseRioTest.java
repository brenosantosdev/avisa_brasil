package br.com.avisabrasil.previsao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.integracoes.Situacao;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnaliseRioTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    @Test
    void analisarDadosReaisDeSaoGabriel() {
        SerieGlofas serie = GlofasConversor.converterPrevisao(Fixtures.ler(Fixtures.GLOFAS_PREVISAO));
        SerieHistorica historico = GlofasConversor.converterHistorico(Fixtures.ler(Fixtures.GLOFAS_HISTORICO));

        PrevisaoRio rio = AnaliseRio.analisar("Rio Negro", serie, historico, Situacao.ATUALIZADO, HOJE);
        var resumo = rio.resumo();

        assertThat(resumo.vazaoHoje()).isEqualTo(3898.0);
        // 3898 → 3810 em 7 dias: -2,3%, dentro de ±5% → estável
        assertThat(resumo.variacaoPercentual7Dias()).isEqualTo(-2.3);
        assertThat(resumo.tendencia()).isEqualTo(Tendencia.ESTAVEL);
        assertThat(resumo.mudancaRapida()).isFalse();
        assertThat(resumo.dataPico()).isEqualTo(LocalDate.of(2026, 12, 10));
        // normal de novembro fica entre ~7.500 e ~9.300 m³/s: o rio está muito abaixo
        assertThat(resumo.comparacaoHoje()).isEqualTo(ComparacaoNormal.MUITO_ABAIXO);

        var diaHoje = rio.dias().stream().filter(d -> d.data().equals(HOJE)).findFirst().orElseThrow();
        assertThat(diaHoje.previsao()).isFalse();
        assertThat(diaHoje.normalP25()).isEqualTo(7481.0);
        assertThat(rio.dias().get(rio.dias().size() - 1).previsao()).isTrue();
    }

    @Test
    void subidaRapida() {
        SerieGlofas serie = serieLinear(1000, 50); // +50 m³/s por dia
        PrevisaoRio rio = AnaliseRio.analisar("Rio", serie, null, Situacao.INDISPONIVEL, HOJE);

        // 1000 → 1350 em 7 dias = +35%
        assertThat(rio.resumo().tendencia()).isEqualTo(Tendencia.SUBINDO);
        assertThat(rio.resumo().variacaoPercentual7Dias()).isEqualTo(35.0);
        assertThat(rio.resumo().mudancaRapida()).isTrue();
    }

    @Test
    void descidaDevagar() {
        SerieGlofas serie = serieLinear(1000, -10); // -70 em 7 dias = -7%
        PrevisaoRio rio = AnaliseRio.analisar("Rio", serie, null, Situacao.INDISPONIVEL, HOJE);

        assertThat(rio.resumo().tendencia()).isEqualTo(Tendencia.DESCENDO);
        assertThat(rio.resumo().mudancaRapida()).isFalse();
    }

    @Test
    void semHistoricoNaoComparaComNormal() {
        PrevisaoRio rio = AnaliseRio.analisar("Rio", serieLinear(1000, 0), null, Situacao.INDISPONIVEL, HOJE);

        assertThat(rio.resumo().comparacaoHoje()).isNull();
        assertThat(rio.resumo().normalHoje()).isNull();
        assertThat(rio.dias()).allMatch(d -> d.normalMediana() == null);
        assertThat(rio.historico().situacao()).isEqualTo(Situacao.INDISPONIVEL);
    }

    @Test
    void compararComFaixaNormal() {
        Map<LocalDate, Double> valores = new HashMap<>();
        // 10 anos, 15 dias em volta de 11/11, valores 1..150
        double v = 1;
        for (int ano = 2016; ano < 2026; ano++) {
            for (int d = -7; d <= 7; d++) {
                valores.put(LocalDate.of(ano, 11, 11).plusDays(d), v++);
            }
        }
        FaixaNormal normal = new SerieHistorica(valores).normalPara(HOJE).orElseThrow();

        assertThat(normal.comparar(5)).isEqualTo(ComparacaoNormal.MUITO_ABAIXO);
        assertThat(normal.comparar(30)).isEqualTo(ComparacaoNormal.ABAIXO);
        assertThat(normal.comparar(75)).isEqualTo(ComparacaoNormal.NORMAL);
        assertThat(normal.comparar(125)).isEqualTo(ComparacaoNormal.ACIMA);
        assertThat(normal.comparar(149)).isEqualTo(ComparacaoNormal.MUITO_ACIMA);
    }

    /** 30 dias para trás e 30 para frente, mudando {@code porDia} m³/s a cada dia a partir de hoje. */
    private static SerieGlofas serieLinear(double hoje, double porDia) {
        List<SerieGlofas.Dia> dias = new ArrayList<>();
        for (int i = -30; i <= 30; i++) {
            double valor = hoje + porDia * i;
            dias.add(new SerieGlofas.Dia(HOJE.plusDays(i), valor, valor, valor, valor));
        }
        return new SerieGlofas(0, 0, dias);
    }
}
