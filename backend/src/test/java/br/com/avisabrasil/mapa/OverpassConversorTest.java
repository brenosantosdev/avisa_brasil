package br.com.avisabrasil.mapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.avisabrasil.Fixtures;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OverpassConversorTest {

    /** Resposta real do Overpass (raio de 8 km da sede, 12/11/2026): 14 elementos, um deles farmácia. */
    private final PontosOsm osm = OverpassConversor.converter(Fixtures.ler(Fixtures.OVERPASS));

    @Test
    void ficarSoComPontosDeApoioReais() {
        // a farmácia (Drogaria Econômica) não é ponto de apoio
        assertThat(osm.pontos()).hasSize(13);
        assertThat(osm.pontos()).noneMatch(p -> "Drogaria Econômica".equals(p.nome()));
        assertThat(osm.dadosOsmDe()).isEqualTo(Instant.parse("2026-11-12T00:05:18Z"));
    }

    @Test
    void classificarPelasEtiquetasDoOsm() {
        Map<TipoPonto, Long> porTipo = osm.pontos().stream()
                .collect(Collectors.groupingBy(PontosOsm.Ponto::tipo, Collectors.counting()));
        assertThat(porTipo)
                .containsEntry(TipoPonto.ESCOLA, 8L)   // 6 escolas + 2 instituições de ensino superior
                .containsEntry(TipoPonto.SAUDE, 2L)    // 2 hospitais
                .containsEntry(TipoPonto.APOIO, 3L)    // polícia, prefeitura, centro comunitário
                .doesNotContainKey(TipoPonto.ABRIGO);  // nenhum abrigo mapeado no OSM
    }

    @Test
    void usarCentroDasAreasELinkDoObjeto() {
        Map<String, PontosOsm.Ponto> porId = osm.pontos().stream()
                .collect(Collectors.toMap(PontosOsm.Ponto::id, Function.identity()));
        var hospital = porId.get("osm:way/819209025");
        assertThat(hospital.nome()).isEqualTo("Hospital de Guarnição de São Gabriel da Cachoeira");
        assertThat(hospital.tipo()).isEqualTo(TipoPonto.SAUDE);
        assertThat(hospital.latitude()).isBetween(-0.122, -0.121);
        assertThat(hospital.link()).isEqualTo("https://www.openstreetmap.org/way/819209025");

        // sem nome no OSM: fica nulo (a tela mostra "sem nome no OpenStreetMap")
        assertThat(porId.get("osm:way/1446881684").nome()).isNull();
        assertThat(porId.get("osm:way/1446881684").tipo()).isEqualTo(TipoPonto.APOIO);
    }

    @Test
    void reconhecerAbrigosDeEmergencia() {
        String json = """
                {"osm3s":{"timestamp_osm_base":"2026-11-12T00:00:00Z"},"elements":[
                  {"type":"node","id":1,"lat":-0.13,"lon":-67.09,"tags":{"emergency":"assembly_point","name":"Ponto de encontro"}},
                  {"type":"node","id":2,"lat":-0.13,"lon":-67.09,"tags":{"amenity":"social_facility","social_facility":"shelter"}},
                  {"type":"node","id":3,"lat":-0.13,"lon":-67.09,"tags":{"healthcare":"pharmacy"}},
                  {"type":"way","id":4,"tags":{"amenity":"school"}}
                ]}
                """;
        var pontos = OverpassConversor.converter(json).pontos();
        // a farmácia e a escola sem coordenada ficam de fora
        assertThat(pontos).extracting(PontosOsm.Ponto::tipo).containsExactly(TipoPonto.ABRIGO, TipoPonto.ABRIGO);
    }

    @Test
    void recusarRespostaDeErro() {
        assertThatThrownBy(() -> OverpassConversor.converter("<html>Too Many Requests</html>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OverpassConversor.converter("{\"remark\":\"runtime error: timeout\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }
}
