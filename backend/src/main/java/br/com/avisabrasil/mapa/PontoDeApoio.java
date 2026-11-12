package br.com.avisabrasil.mapa;

/**
 * Ponto mostrado no mapa.
 *
 * @param id "osm:way/123" ou "dc:5"
 * @param nome pode ser nulo quando o OpenStreetMap não tem nome
 * @param link página do objeto no OpenStreetMap (nulo para pontos da Defesa Civil)
 * @param setorDeRisco código do setor de risco do SGB em que o ponto fica, ou nulo
 * @param distanciaDaSedeMetros distância em linha reta até o centro da cidade
 */
public record PontoDeApoio(
        String id,
        String nome,
        TipoPonto tipo,
        OrigemPonto origem,
        double latitude,
        double longitude,
        String observacao,
        Integer capacidade,
        String link,
        String setorDeRisco,
        String grauRiscoSetor,
        long distanciaDaSedeMetros) {
}
