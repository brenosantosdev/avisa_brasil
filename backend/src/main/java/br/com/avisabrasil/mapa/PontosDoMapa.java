package br.com.avisabrasil.mapa;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Todos os pontos do mapa: os do OpenStreetMap e os cadastrados pela Defesa Civil.
 *
 * @param dadosOsmDe data da base do OpenStreetMap usada (nula se o OSM ainda não respondeu)
 */
public record PontosDoMapa(
        OffsetDateTime dadosOsmDe,
        int totalOpenStreetMap,
        int totalDefesaCivil,
        List<PontoDeApoio> pontos) {
}
