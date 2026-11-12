package br.com.avisabrasil.previsao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.avisabrasil.Fixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OpenMeteoConversorTest {

    private final PrevisaoTempo previsao = OpenMeteoConversor.converter(Fixtures.ler(Fixtures.OPEN_METEO));

    @Test
    void lerSeteDiasComValoresDaResposta() {
        assertThat(previsao.dias()).hasSize(7);
        var hoje = previsao.dias().get(0);
        assertThat(hoje.data()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(hoje.chuvaMm()).isEqualTo(4.2);
        assertThat(hoje.chanceChuva()).isEqualTo(90);
        assertThat(hoje.temperaturaMaxima()).isEqualTo(29.7);
        assertThat(hoje.temperaturaMinima()).isEqualTo(24.1);
        assertThat(previsao.latitude()).isEqualTo(-0.10544816);
    }

    @Test
    void explicarChuvaEmCategoriaEBaldes() {
        var hoje = previsao.dias().get(0);
        assertThat(hoje.categoriaChuva()).isEqualTo(CategoriaChuva.FRACA);
        assertThat(hoje.baldesPorM2()).isEqualTo(0.2);
        // código WMO 53 = garoa → chuva
        assertThat(hoje.ceu()).isEqualTo(Ceu.CHUVA);
        assertThat(hoje.calorForte()).isFalse();
    }

    @Test
    void somarChuvaHorariaPorParteDoDia() {
        var amanha = previsao.dias().get(1);
        assertThat(amanha.chuvaPorPeriodo())
                .containsEntry(Periodo.MADRUGADA, 0.0)
                .containsEntry(Periodo.MANHA, 1.1)
                .containsEntry(Periodo.TARDE, 5.5)
                .containsEntry(Periodo.NOITE, 0.0);
        assertThat(amanha.periodoMaisChuvoso()).isEqualTo(Periodo.TARDE);
    }

    @Test
    void recusarRespostaSemDadosDiarios() {
        assertThatThrownBy(() -> OpenMeteoConversor.converter("{\"error\":true,\"reason\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenMeteoConversor.converter("<html>erro</html>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceitarValoresNulos() {
        String json = """
                {"latitude":0,"longitude":0,
                 "daily":{"time":["2026-11-11"],"precipitation_sum":[null],
                          "precipitation_probability_max":[null],"temperature_2m_max":[null],
                          "temperature_2m_min":[null],"weather_code":[null]}}
                """;
        var dia = OpenMeteoConversor.converter(json).dias().get(0);
        assertThat(dia.chuvaMm()).isZero();
        assertThat(dia.chanceChuva()).isNull();
        assertThat(dia.ceu()).isNull();
        assertThat(dia.periodoMaisChuvoso()).isNull();
    }
}
