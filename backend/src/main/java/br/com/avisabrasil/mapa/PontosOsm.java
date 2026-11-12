package br.com.avisabrasil.mapa;

import java.time.Instant;
import java.util.List;

/**
 * Pontos vindos do OpenStreetMap.
 *
 * @param dadosOsmDe momento da base do OSM usada pelo servidor Overpass
 */
public record PontosOsm(Instant dadosOsmDe, List<Ponto> pontos) {

    /** @param osmTipo node, way ou relation */
    public record Ponto(String osmTipo, long osmId, String nome, TipoPonto tipo, double latitude, double longitude) {

        public String id() {
            return "osm:" + osmTipo + "/" + osmId;
        }

        public String link() {
            return "https://www.openstreetmap.org/" + osmTipo + "/" + osmId;
        }
    }
}
