package br.com.avisabrasil.previsao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Regras que transformam números em palavras e comparações do dia a dia. */
class ExplicacoesTest {

    @ParameterizedTest
    @CsvSource({
        "0, SEM_CHUVA", "0.9, SEM_CHUVA", "1, FRACA", "9.9, FRACA", "10, MODERADA",
        "24.9, MODERADA", "25, FORTE", "45, FORTE", "50, MUITO_FORTE", "120, MUITO_FORTE"
    })
    void classificarChuvaDoDia(double mm, CategoriaChuva esperada) {
        assertThat(CategoriaChuva.doTotalDiario(mm)).isEqualTo(esperada);
    }

    @Test
    void quarentaECincoMilimetrosSaoMaisDeDoisBaldes() {
        // exemplo do CLAUDE.md: 45 mm = "mais de 2 baldes por metro quadrado"
        assertThat(Baldes.porMetroQuadrado(45)).isEqualTo(2.3);
    }

    @Test
    void vazaoEmBaldesPorSegundo() {
        // 1 m³/s = 1000 litros por segundo = 50 baldes de 20 litros
        assertThat(Baldes.porSegundo(1)).isEqualTo(50);
        assertThat(Baldes.porSegundo(3898.42)).isEqualTo(194_921);
    }

    @ParameterizedTest
    @CsvSource({"0, LIMPO", "2, NUBLADO", "45, NEBLINA", "61, CHUVA", "81, CHUVA", "95, TROVOADA"})
    void simplificarCodigoDoCeu(int codigo, Ceu esperado) {
        assertThat(Ceu.doCodigoWmo(codigo)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"0, MADRUGADA", "5, MADRUGADA", "6, MANHA", "12, TARDE", "17, TARDE", "18, NOITE", "23, NOITE"})
    void dividirODiaEmPartes(int hora, Periodo esperado) {
        assertThat(Periodo.doHorario(hora)).isEqualTo(esperado);
    }
}
