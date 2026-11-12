package br.com.avisabrasil.risco;

import br.com.avisabrasil.rio.CotasDeReferencia;
import br.com.avisabrasil.rio.Nivel;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Nível de risco da comunidade: o maior entre todos os fatores (cheia ou seca).
 *
 * @param principal o fator que decidiu o nível (nulo quando Normal)
 * @param fatores todos os fatores ativos, para a tela explicar
 * @param ana avaliação da régua da ANA (nula se não houver dado)
 * @param sensor avaliação do sensor (nula se o sensor não estiver ativo)
 * @param simulado o nível foi decidido por dado simulado
 */
public record AvaliacaoDeRisco(
        Nivel nivel,
        TipoRisco tipo,
        Fator principal,
        List<Fator> fatores,
        AvaliacaoMedicao ana,
        AvaliacaoMedicao sensor,
        SensorInfo sensorInfo,
        CotasDeReferencia cotas,
        OffsetDateTime avaliadoEm,
        boolean simulado) {

    /**
     * @param ativo recebeu leitura há pouco tempo
     * @param dataHora horário da medição (no simulador, tempo simulado)
     */
    public record SensorInfo(String codigo, String nome, boolean simulado, boolean ativo, Double nivelCm,
            OffsetDateTime dataHora, OffsetDateTime recebidoEm) {
    }
}
