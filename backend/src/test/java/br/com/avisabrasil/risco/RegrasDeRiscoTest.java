package br.com.avisabrasil.risco;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.previsao.FaixaNormal;
import br.com.avisabrasil.rio.CotasDeReferencia;
import br.com.avisabrasil.rio.Nivel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regras do motor com as cotas provisórias reais de Curicuriari (11/2026). */
class RegrasDeRiscoTest {

    private static final CotasDeReferencia COTAS = Fixtures.COTAS_CURICURIARI;
    private static final FaixaNormal NOVEMBRO = Fixtures.NORMAL_NOVEMBRO;

    private static AvaliacaoMedicao avaliar(double cota, Double velocidade) {
        return RegrasDeRisco.avaliarMedicao(FonteRisco.ANA, false, cota, velocidade, NOVEMBRO, COTAS, 24, 168);
    }

    @Test
    void hojeOSecaPelaEpoca() {
        // 11/11/2026: 7,74 m e descendo 2,4 cm/dia; acima das cotas de seca, mas entre os 25% mais baixos da época
        var a = avaliar(774, -2.4);

        assertThat(a.nivel()).isEqualTo(Nivel.ATENCAO);
        assertThat(a.tipo()).isEqualTo(TipoRisco.SECA);
        assertThat(a.nivelCheia()).isEqualTo(Nivel.NORMAL);
        assertThat(a.fatores()).singleElement().satisfies(f -> {
            assertThat(f.motivo()).isEqualTo(Fator.Motivo.SECA_EPOCA);
            assertThat(f.dados()).containsEntry("percentual", 25);
        });
        // próxima: cota de Alerta de seca (5,89 m), faltam 1,85 m; a 2,4 cm/dia são 1850 horas
        assertThat(a.proximo().tipo()).isEqualTo(TipoRisco.SECA);
        assertThat(a.proximo().nivel()).isEqualTo(Nivel.ALERTA);
        assertThat(a.proximo().faltaCm()).isEqualTo(185);
        assertThat(a.proximo().horas()).isEqualTo(1850.0);
    }

    @Test
    void secaPelaEpocaFicaMaisGraveQuantoMaisBaixo() {
        assertThat(avaliar(740, null).nivelSeca()).isEqualTo(Nivel.ALERTA);
        assertThat(avaliar(700, null).nivelSeca()).isEqualTo(Nivel.EMERGENCIA);
    }

    @Test
    void secaPelasCotas() {
        var a = avaliar(520, null);
        assertThat(a.nivel()).isEqualTo(Nivel.EMERGENCIA);
        assertThat(a.tipo()).isEqualTo(TipoRisco.SECA);
        assertThat(a.fatores()).extracting(Fator::motivo).contains(Fator.Motivo.COTA_SECA, Fator.Motivo.SECA_EPOCA);
    }

    @Test
    void cheiaPelasCotas() {
        assertThat(avaliar(1390, null).nivel()).isEqualTo(Nivel.ATENCAO);
        assertThat(avaliar(1420, null).nivel()).isEqualTo(Nivel.ALERTA);
        var a = avaliar(1480, null);
        assertThat(a.nivel()).isEqualTo(Nivel.EMERGENCIA);
        assertThat(a.tipo()).isEqualTo(TipoRisco.CHEIA);
        assertThat(a.proximo()).isNull();
    }

    @Test
    void sobeAntesQuandoAProximaCotaChegaLogo() {
        // 14,00 m subindo 6 cm/h (144 cm/dia): faltam 17 cm para o Alerta = menos de 3 horas
        var a = avaliar(1400, 144.0);

        assertThat(a.nivelCheia()).isEqualTo(Nivel.ATENCAO);
        assertThat(a.nivel()).isEqualTo(Nivel.ALERTA);
        Fator principal = RegrasDeRisco.principal(a.fatores()).orElseThrow();
        assertThat(principal.preventivo()).isTrue();
        assertThat(principal.motivo()).isEqualTo(Fator.Motivo.PREVENTIVO_CHEIA);
        assertThat(principal.dados()).containsEntry("horas", 2.8);
    }

    @Test
    void naoSobeAntesSeAProximaCotaEstiverLonge() {
        // subindo 1 cm/h: faltam 17 cm para o Alerta = 17 h (dentro de 24 h), sobe antes
        assertThat(avaliar(1400, 24.0).nivel()).isEqualTo(Nivel.ALERTA);
        // faltam 55 cm para a Atenção = 55 h (fora do prazo), não sobe
        assertThat(avaliar(1330, 24.0).nivel()).isEqualTo(Nivel.NORMAL);
    }

    @Test
    void normalSemRisco() {
        var a = avaliar(900, 0.5);
        assertThat(a.nivel()).isEqualTo(Nivel.NORMAL);
        assertThat(a.tipo()).isNull();
        assertThat(a.fatores()).isEmpty();
        // parado: próxima cota na direção da cheia, sem previsão de tempo
        assertThat(a.proximo().tipo()).isEqualTo(TipoRisco.CHEIA);
        assertThat(a.proximo().horas()).isNull();
    }

    @Test
    void emEmpateValeAMedicaoOficial() {
        Fator ana = new Fator(FonteRisco.ANA, TipoRisco.SECA, Nivel.ATENCAO, Fator.Motivo.SECA_EPOCA, Map.of(), false, false);
        Fator sensor = new Fator(FonteRisco.SENSOR, TipoRisco.SECA, Nivel.ATENCAO, Fator.Motivo.SECA_EPOCA, Map.of(), true, false);
        Fator chuva = new Fator(FonteRisco.CHUVA, TipoRisco.CHEIA, Nivel.ATENCAO, Fator.Motivo.CHUVA_FORTE, Map.of(), false, true);
        assertThat(RegrasDeRisco.principal(List.of(chuva, sensor, ana))).contains(ana);

        Fator maior = new Fator(FonteRisco.SENSOR, TipoRisco.CHEIA, Nivel.ALERTA, Fator.Motivo.COTA_CHEIA, Map.of(), true, false);
        assertThat(RegrasDeRisco.principal(List.of(ana, maior))).contains(maior);
    }

    @Test
    void mensagemDeAlertaEmPortuguesSimples() {
        var a = avaliar(774, -2.4);
        Fator f = a.fatores().get(0);
        assertThat(MensagensDeRisco.explicar(f))
                .isEqualTo("O rio está em 7,74 m: entre os 25% níveis mais baixos para esta época do ano (1978–2025). (medição da ANA)");
        assertThat(MensagensDeRisco.titulo(Nivel.ALERTA, TipoRisco.CHEIA)).isEqualTo("ALERTA – CHEIA");
    }
}
