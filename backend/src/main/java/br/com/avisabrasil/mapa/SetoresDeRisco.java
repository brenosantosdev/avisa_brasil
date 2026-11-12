package br.com.avisabrasil.mapa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Setores de risco oficiais do Serviço Geológico do Brasil (SGB/CPRM), setorização de 2022.
 * O arquivo é gerado por ferramentas/sgb_para_geojson.py a partir do shapefile publicado no RiGeo
 * e também é servido ao navegador em /dados/.
 */
@Component
public class SetoresDeRisco {

    public static final String ARQUIVO = "static/dados/sgb-setores-risco-sao-gabriel-2022.geojson";

    /** @param poligonos cada polígono é uma lista de anéis; o primeiro é o externo, os outros são furos */
    public record Setor(String codigo, String grauRisco, List<List<List<double[]>>> poligonos) {

        boolean contem(double lat, double lon) {
            for (List<List<double[]>> poligono : poligonos) {
                if (Geo.dentroDoAnel(lat, lon, poligono.get(0))
                        && poligono.stream().skip(1).noneMatch(furo -> Geo.dentroDoAnel(lat, lon, furo))) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<Setor> setores;

    public SetoresDeRisco(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(ARQUIVO).getInputStream()) {
            this.setores = ler(mapper.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, e);
        }
    }

    public List<Setor> setores() {
        return setores;
    }

    /** Setor de risco em que o ponto fica, se houver. */
    public Optional<Setor> setorEm(double lat, double lon) {
        return setores.stream().filter(s -> s.contem(lat, lon)).findFirst();
    }

    static List<Setor> ler(JsonNode geojson) {
        List<Setor> lista = new ArrayList<>();
        for (JsonNode feicao : geojson.path("features")) {
            JsonNode geometria = feicao.path("geometry");
            JsonNode coordenadas = geometria.path("coordinates");
            List<List<List<double[]>>> poligonos = new ArrayList<>();
            if ("Polygon".equals(geometria.path("type").asText())) {
                poligonos.add(aneis(coordenadas));
            } else if ("MultiPolygon".equals(geometria.path("type").asText())) {
                coordenadas.forEach(p -> poligonos.add(aneis(p)));
            }
            JsonNode prop = feicao.path("properties");
            lista.add(new Setor(prop.path("setor").asText(), prop.path("grauRisco").asText(null), poligonos));
        }
        return List.copyOf(lista);
    }

    private static List<List<double[]>> aneis(JsonNode poligono) {
        List<List<double[]>> aneis = new ArrayList<>();
        for (JsonNode anel : poligono) {
            List<double[]> pontos = new ArrayList<>();
            anel.forEach(p -> pontos.add(new double[] {p.get(0).asDouble(), p.get(1).asDouble()}));
            aneis.add(pontos);
        }
        return aneis;
    }
}
