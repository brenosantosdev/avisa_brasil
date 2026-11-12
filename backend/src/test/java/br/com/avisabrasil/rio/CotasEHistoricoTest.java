package br.com.avisabrasil.rio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.previsao.ComparacaoNormal;
import java.time.LocalDate;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CotasEHistoricoTest {

    /** 20 anos completos; a cheia do ano N chega a 1000 + 10*N cm em 15/06, a seca a 300 - N cm em 15/11. */
    private static TreeMap<LocalDate, Double> serieSintetica() {
        TreeMap<LocalDate, Double> serie = new TreeMap<>();
        for (int n = 0; n < 20; n++) {
            int ano = 2000 + n;
            for (LocalDate d = LocalDate.of(ano, 1, 1); d.getYear() == ano; d = d.plusDays(1)) {
                serie.put(d, 600.0);
            }
            serie.put(LocalDate.of(ano, 6, 15), 1000.0 + 10 * n);
            serie.put(LocalDate.of(ano, 11, 15), 300.0 - n);
        }
        // ano incompleto (só 10 dias) não entra
        for (int d = 1; d <= 10; d++) {
            serie.put(LocalDate.of(2020, 1, d), 5000.0);
        }
        return serie;
    }

    @Test
    void maioresCheiasESecasComDatas() {
        var extremos = new ExtremosAnuais(serieSintetica());

        assertThat(extremos.anosCompletos()).isEqualTo(20);
        assertThat(extremos.maioresCheias(3)).extracting(ExtremosAnuais.Evento::ano).containsExactly(2019, 2018, 2017);
        assertThat(extremos.maioresCheias(1).get(0).data()).isEqualTo(LocalDate.of(2019, 6, 15));
        assertThat(extremos.maioresSecas(2)).extracting(ExtremosAnuais.Evento::cotaCm).containsExactly(281.0, 282.0);
    }

    @Test
    void cotasProvisoriasPelasCheiasAnuais() {
        var cotas = CotasDeReferencia.definir(semCotas(), new ExtremosAnuais(serieSintetica())).orElseThrow();

        // cheias: 1000, 1010, ..., 1190 → mediana 1095, p80 1152, p95 1180,5
        assertThat(cotas.origem()).isEqualTo(CotasDeReferencia.Origem.PROVISORIA);
        assertThat(cotas.atencaoCm()).isEqualTo(1095);
        assertThat(cotas.alertaCm()).isEqualTo(1152);
        assertThat(cotas.emergenciaCm()).isEqualTo(1181);
        assertThat(cotas.anoInicial()).isEqualTo(2000);
        assertThat(cotas.anoFinal()).isEqualTo(2019);

        assertThat(cotas.nivel(900)).isEqualTo(Nivel.NORMAL);
        assertThat(cotas.nivel(1100)).isEqualTo(Nivel.ATENCAO);
        assertThat(cotas.nivel(1160)).isEqualTo(Nivel.ALERTA);
        assertThat(cotas.nivel(1181)).isEqualTo(Nivel.EMERGENCIA);
        assertThat(cotas.proximoNivel(1100)).contains(Nivel.ALERTA);
        assertThat(cotas.proximoNivel(1200)).isEmpty();
    }

    @Test
    void cotasConfiguradasExigemFonte() {
        var oficiais = new AvisaBrasilProperties.Cotas(900, 1000, 1100, "Boletim SGB nº X");
        var cotas = CotasDeReferencia.definir(oficiais, null).orElseThrow();
        assertThat(cotas.origem()).isEqualTo(CotasDeReferencia.Origem.OFICIAL_CONFIGURADA);
        assertThat(cotas.fonte()).isEqualTo("Boletim SGB nº X");

        var semFonte = new AvisaBrasilProperties.Cotas(900, 1000, 1100, " ");
        assertThatThrownBy(() -> CotasDeReferencia.definir(semFonte, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void poucoHistoricoNaoGeraCotas() {
        assertThat(CotasDeReferencia.definir(semCotas(),
                new ExtremosAnuais(new TreeMap<>(AnaConversor.cotasDiarias(Fixtures.ler(Fixtures.ANA_COTAS_CIDADE))))))
                .isEmpty();
    }

    @Test
    void tendenciaPelaTelemetriaReal() {
        var leituras = AnaConversor.telemetria(Fixtures.ler(Fixtures.ANA_TELEMETRIA));

        // valores calculados à parte (Python) com as mesmas leituras
        assertThat(RioAgoraServico.variacaoEm24h(leituras)).isEqualTo(-2.0);
        assertThat(RioAgoraServico.velocidadeCmPorDia(leituras)).isEqualTo(-2.5);
        assertThat(RioAgoraServico.porHora(leituras)).hasSize(167);
    }

    @Test
    void anaEGlofasDivergemQuandoApontamLadosDiferentes() {
        assertThat(RioAgoraServico.divergem(ComparacaoNormal.NORMAL, ComparacaoNormal.MUITO_ABAIXO)).isTrue();
        assertThat(RioAgoraServico.divergem(ComparacaoNormal.ACIMA, ComparacaoNormal.ABAIXO)).isTrue();
        assertThat(RioAgoraServico.divergem(ComparacaoNormal.ABAIXO, ComparacaoNormal.MUITO_ABAIXO)).isFalse();
        assertThat(RioAgoraServico.divergem(null, ComparacaoNormal.MUITO_ABAIXO)).isFalse();
    }

    @Test
    void regressaoLinear() {
        assertThat(Estatistica.inclinacao(new double[] {0, 1, 2, 3}, new double[] {10, 12, 14, 16})).isEqualTo(2.0);
    }

    private static AvisaBrasilProperties.Cotas semCotas() {
        return new AvisaBrasilProperties.Cotas(null, null, null, null);
    }
}
