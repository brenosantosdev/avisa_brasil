package br.com.avisabrasil.mapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SetoresDeRiscoTest {

    private final SetoresDeRisco setores = new SetoresDeRisco(new ObjectMapper());

    @Test
    void carregarOsQuatorzeSetoresOficiaisDoSgb() {
        assertThat(setores.setores()).hasSize(14);
        assertThat(setores.setores()).extracting(SetoresDeRisco.Setor::grauRisco)
                .containsOnly("Alto", "Muito Alto");
    }

    @Test
    void arquivoCitaAFonteOficial() throws Exception {
        var geojson = new ObjectMapper().readTree(new ClassPathResource(SetoresDeRisco.ARQUIVO).getInputStream());
        assertThat(geojson.path("fonte").path("url").asText()).isEqualTo("https://rigeo.sgb.gov.br/handle/doc/22730");
        assertThat(geojson.path("fonte").path("nome").asText()).contains("SGB");
    }

    @Test
    void encontrarSetorDeUmPonto() {
        // pontos internos calculados à parte (Python) a partir do mesmo GeoJSON
        assertThat(setores.setorEm(-0.128448, -67.086051))
                .hasValueSatisfying(s -> {
                    assertThat(s.codigo()).isEqualTo("SR_005");
                    assertThat(s.grauRisco()).isEqualTo("Muito Alto");
                });
        assertThat(setores.setorEm(-0.128689, -67.087979)).map(SetoresDeRisco.Setor::codigo).contains("SR_006");
    }

    @Test
    void pontosForaDosSetores() {
        // Hospital de Guarnição (OSM) e o Instituto Federal ficam fora dos setores
        assertThat(setores.setorEm(-0.1213, -67.0905)).isEmpty();
        assertThat(setores.setorEm(-0.1271, -67.0619)).isEmpty();
    }

    @Test
    void dentroDoAnelRespeitaFormato() {
        List<double[]> quadrado = List.of(
                new double[] {0, 0}, new double[] {0, 1}, new double[] {1, 1}, new double[] {1, 0}, new double[] {0, 0});
        assertThat(Geo.dentroDoAnel(0.5, 0.5, quadrado)).isTrue();
        assertThat(Geo.dentroDoAnel(1.5, 0.5, quadrado)).isFalse();
    }

    @Test
    void distanciaEmLinhaReta() {
        // sede → delegacia (OSM): 1063 m, calculado à parte
        assertThat(Geo.distanciaMetros(-0.1294, -67.0917, -0.1201, -67.0938)).isCloseTo(1063, within(15.0));
        // 1 grau de latitude ≈ 111 km
        assertThat(Geo.distanciaMetros(0, 0, 1, 0)).isCloseTo(111_195, within(10.0));
    }
}
