package br.com.avisabrasil.mapa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Converte a resposta do Overpass em pontos de apoio, classificando pelas etiquetas (tags) do OSM. */
public final class OverpassConversor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ESCOLAS = Set.of("school", "college", "university", "kindergarten");
    private static final Set<String> SAUDE_AMENITY = Set.of("hospital", "clinic", "doctors");
    /** healthcare=pharmacy, dentist etc. ficam de fora: não são lugar de abrigo ou socorro. */
    private static final Set<String> SAUDE_HEALTHCARE = Set.of("hospital", "clinic", "centre", "doctor");
    private static final Set<String> APOIO = Set.of("community_centre", "townhall", "police", "fire_station");
    private static final Set<String> ABRIGO_EMERGENCY = Set.of("assembly_point", "shelter");

    private OverpassConversor() {
    }

    public static PontosOsm converter(String json) {
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Resposta do Overpass não é JSON: " + e.getOriginalMessage(), e);
        }
        JsonNode elementos = raiz.path("elements");
        if (!elementos.isArray()) {
            String remark = raiz.path("remark").asText("");
            throw new IllegalArgumentException("Resposta do Overpass sem elementos " + remark);
        }
        String base = raiz.path("osm3s").path("timestamp_osm_base").asText(null);

        List<PontosOsm.Ponto> pontos = new ArrayList<>();
        for (JsonNode e : elementos) {
            JsonNode tags = e.path("tags");
            Optional<TipoPonto> tipo = classificar(tags);
            JsonNode posicao = e.has("center") ? e.path("center") : e;
            if (tipo.isEmpty() || !posicao.has("lat") || !posicao.has("lon")) {
                continue;
            }
            String nome = tags.path("name").asText("").strip();
            pontos.add(new PontosOsm.Ponto(
                    e.path("type").asText(),
                    e.path("id").asLong(),
                    nome.isEmpty() ? null : nome,
                    tipo.get(),
                    posicao.path("lat").asDouble(),
                    posicao.path("lon").asDouble()));
        }
        pontos.sort(Comparator.comparing(PontosOsm.Ponto::id));
        return new PontosOsm(base == null ? null : Instant.parse(base), pontos);
    }

    static Optional<TipoPonto> classificar(JsonNode tags) {
        String amenity = tags.path("amenity").asText("");
        String healthcare = tags.path("healthcare").asText("");
        if (ABRIGO_EMERGENCY.contains(tags.path("emergency").asText(""))
                || "shelter".equals(tags.path("social_facility").asText(""))) {
            return Optional.of(TipoPonto.ABRIGO);
        }
        if (SAUDE_AMENITY.contains(amenity) || SAUDE_HEALTHCARE.contains(healthcare)) {
            return Optional.of(TipoPonto.SAUDE);
        }
        if (ESCOLAS.contains(amenity)) {
            return Optional.of(TipoPonto.ESCOLA);
        }
        if (APOIO.contains(amenity)) {
            return Optional.of(TipoPonto.APOIO);
        }
        return Optional.empty();
    }
}
