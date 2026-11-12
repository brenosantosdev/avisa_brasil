package br.com.avisabrasil.previsao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import br.com.avisabrasil.Fixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GlofasConversorTest {

    @Test
    void lerPrevisaoComFaixaDoConjunto() {
        SerieGlofas serie = GlofasConversor.converterPrevisao(Fixtures.ler(Fixtures.GLOFAS_PREVISAO));

        assertThat(serie.dias()).hasSize(60);
        assertThat(serie.latitude()).isEqualTo(-0.17499924);
        var dia = serie.dia(LocalDate.of(2026, 12, 10)).orElseThrow();
        assertThat(dia.vazao()).isEqualTo(3907.41);
        assertThat(dia.mediana()).isEqualTo(5674.45);
        assertThat(dia.p25()).isEqualTo(4730.71);
        assertThat(dia.p75()).isEqualTo(7210.55);
        assertThat(dia.valorPrincipal()).isEqualTo(5674.45);
    }

    @Test
    void lerHistoricoECalcularNormalDaEpoca() {
        SerieHistorica historico = GlofasConversor.converterHistorico(Fixtures.ler(Fixtures.GLOFAS_HISTORICO));

        // valores esperados calculados à parte (Python) a partir da mesma fixture
        FaixaNormal normal = historico.normalPara(LocalDate.of(2026, 11, 11)).orElseThrow();
        assertThat(normal.amostras()).isEqualTo(435);
        assertThat(normal.anoInicial()).isEqualTo(1997);
        assertThat(normal.anoFinal()).isEqualTo(2025);
        assertThat(normal.janelaDias()).isEqualTo(7); // 29 anos × 15 dias = 435
        assertThat(normal.p10()).isCloseTo(5933.72, within(0.01));
        assertThat(normal.p25()).isCloseTo(7480.72, within(0.01));
        assertThat(normal.mediana()).isCloseTo(8471.27, within(0.01));
        assertThat(normal.p75()).isCloseTo(9342.30, within(0.01));

        // seca histórica do Rio Negro em 2023
        assertThat(historico.minimo().orElseThrow().getKey()).isEqualTo(LocalDate.of(2023, 10, 24));
    }

    @Test
    void recusarRespostaVazia() {
        String semValores = "{\"daily\":{\"time\":[\"1984-01-01\"],\"river_discharge\":[null]}}";
        assertThatThrownBy(() -> GlofasConversor.converterHistorico(semValores))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GlofasConversor.converterPrevisao(semValores))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
