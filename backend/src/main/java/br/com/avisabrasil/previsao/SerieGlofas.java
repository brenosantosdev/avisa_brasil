package br.com.avisabrasil.previsao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Vazão do rio por dia, como veio do GloFAS: últimos dias e previsão.
 *
 * @param latitude latitude da célula da grade do GloFAS usada
 * @param longitude longitude da célula da grade do GloFAS usada
 */
public record SerieGlofas(double latitude, double longitude, List<Dia> dias) {

    /**
     * @param vazao vazão do membro de controle (m³/s)
     * @param mediana mediana do conjunto de previsões (m³/s)
     * @param p25 25% das previsões ficam abaixo deste valor
     * @param p75 75% das previsões ficam abaixo deste valor
     */
    public record Dia(LocalDate data, Double vazao, Double mediana, Double p25, Double p75) {

        /** Valor principal: a mediana do conjunto quando existe; senão, a vazão de controle. */
        public Double valorPrincipal() {
            return mediana != null ? mediana : vazao;
        }
    }

    public Optional<Dia> dia(LocalDate data) {
        return dias.stream().filter(d -> d.data().equals(data)).findFirst();
    }
}
