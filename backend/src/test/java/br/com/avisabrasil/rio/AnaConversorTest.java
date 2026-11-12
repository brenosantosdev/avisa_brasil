package br.com.avisabrasil.rio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.avisabrasil.Fixtures;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AnaConversorTest {

    @Test
    void lerTelemetriaDeCuricuriari() {
        var leituras = AnaConversor.telemetria(Fixtures.ler(Fixtures.ANA_TELEMETRIA));

        assertThat(leituras).hasSize(668);
        assertThat(leituras.get(0).dataHora()).isEqualTo(LocalDateTime.of(2026, 11, 4, 21, 0));
        var ultima = leituras.get(leituras.size() - 1);
        assertThat(ultima.dataHora()).isEqualTo(LocalDateTime.of(2026, 11, 11, 19, 45));
        assertThat(ultima.cotaCm()).isEqualTo(774.0);
        assertThat(ultima.vazaoM3s()).isEqualTo(6382.09);
    }

    @Test
    void horarioDaAnaEmBrasiliaViraHorarioDeManaus() {
        // a ANA manda "2026-11-11 20:45:00.0" no horário de Brasília (UTC−3) = 19:45 em Manaus (UTC−4)
        var ultima = AnaConversor.telemetria(Fixtures.ler(Fixtures.ANA_TELEMETRIA)).get(667);
        assertThat(ultima.dataHora()).isEqualTo(LocalDateTime.of(2026, 11, 11, 19, 45));
    }

    @Test
    void semRegistrosNoPeriodoDevolveListaVazia() {
        // resposta real para a estação da cidade, cuja telemetria foi encerrada em 2017
        assertThat(AnaConversor.telemetria(Fixtures.ler(Fixtures.ANA_SEM_REGISTROS))).isEmpty();
    }

    @Test
    void cotaDiariaUsaAMediaDiariaDoMes() {
        var diarias = AnaConversor.cotasDiarias(Fixtures.ler(Fixtures.ANA_COTAS_CIDADE));

        // janeiro a março de 2026 (a ANA ainda não publicou os meses seguintes)
        assertThat(diarias).hasSize(31 + 28 + 31);
        assertThat(diarias.get(LocalDate.of(2026, 1, 1))).isEqualTo(626.5);
        assertThat(diarias.get(LocalDate.of(2026, 1, 31))).isEqualTo(772.5);
        assertThat(diarias.keySet()).allMatch(d -> d.getYear() == 2026 && d.getMonthValue() <= 3);
    }

    @Test
    void prefereDadoConsistidoAoBruto() {
        String json = """
                {"status":"OK","items":[
                  {"Data_Hora_Dado":"1990-05-01 00:00:00.0","Mediadiaria":"1","nivelconsistencia":"1","Cota_01":"100"},
                  {"Data_Hora_Dado":"1990-05-01 00:00:00.0","Mediadiaria":"1","nivelconsistencia":"2","Cota_01":"110"},
                  {"Data_Hora_Dado":"1990-06-01 07:00:00.0","Mediadiaria":"0","nivelconsistencia":"1","Cota_01":"200"},
                  {"Data_Hora_Dado":"1990-06-01 17:00:00.0","Mediadiaria":"0","nivelconsistencia":"1","Cota_01":"210"}
                ]}
                """;
        var diarias = AnaConversor.cotasDiarias(json);
        assertThat(diarias.get(LocalDate.of(1990, 5, 1))).isEqualTo(110.0);
        // sem média diária: média das leituras das 7h e 17h
        assertThat(diarias.get(LocalDate.of(1990, 6, 1))).isEqualTo(205.0);
    }

    @Test
    void recusarRespostaDeErro() {
        assertThatThrownBy(() -> AnaConversor.telemetria(
                "{\"status\":\"401 UNAUTHORIZED\",\"message\":\"Token de Autenticação da API Inexistente\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("401");
    }
}
