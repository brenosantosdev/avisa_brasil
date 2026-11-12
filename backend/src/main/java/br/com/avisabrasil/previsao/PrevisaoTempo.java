package br.com.avisabrasil.previsao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Previsão do tempo para os próximos dias na comunidade. */
public record PrevisaoTempo(double latitude, double longitude, List<Dia> dias) {

    /** Temperatura máxima a partir da qual o dia é marcado como "calor forte". */
    public static final double CALOR_FORTE_GRAUS = 35.0;

    /**
     * @param chuvaPorPeriodo milímetros de chuva em cada parte do dia
     * @param periodoMaisChuvoso parte do dia com mais chuva, ou {@code null} se quase não chover
     */
    public record Dia(
            LocalDate data,
            double chuvaMm,
            Integer chanceChuva,
            Double temperaturaMaxima,
            Double temperaturaMinima,
            Ceu ceu,
            CategoriaChuva categoriaChuva,
            double baldesPorM2,
            Map<Periodo, Double> chuvaPorPeriodo,
            Periodo periodoMaisChuvoso,
            boolean calorForte) {
    }
}
