package br.com.avisabrasil.avisos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Avisos reais do INMET (novembro de 2026) salvos em src/test/resources/inmet. */
class InmetConversorTest {

    private static final String SAO_GABRIEL = "1303809";

    private static String ler(String arquivo) throws IOException {
        try (InputStream entrada = InmetConversorTest.class.getResourceAsStream("/inmet/" + arquivo)) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void escolheNoRssOsAvisosDaMesorregiaoQueNaoTerminaramHaMuitoTempo() throws IOException {
        String rss = ler("avisos-rss.xml");
        // 13/11 00:00 em Brasília: o aviso de 04/11 já tinha terminado; o 56399 é de outras mesorregiões
        Instant desde = Instant.parse("2026-11-13T03:00:00Z");

        assertThat(InmetConversor.candidatos(rss, "Norte Amazonense", desde)).containsExactly("56396", "56392");
        assertThat(InmetConversor.candidatos(rss, "Norte Amazonense", Instant.parse("2026-11-01T00:00:00Z")))
                .containsExactlyInAnyOrder("56396", "56392", "56268");
        assertThat(InmetConversor.candidatos(rss, "Mesorregião que não existe", desde)).isEmpty();
    }

    @Test
    void avisoQueIncluiOMunicipioPeloCodigoDoIbge() throws IOException {
        var aviso = InmetConversor.lerCap("56392", ler("cap-56392-com-sao-gabriel.xml"), SAO_GABRIEL).orElseThrow();

        assertThat(aviso.evento()).isEqualTo("Tempestade");
        assertThat(aviso.eventoCodigo()).isEqualTo("TEMPESTADE");
        assertThat(aviso.grau()).isEqualTo(AvisoInmet.Grau.PERIGO_POTENCIAL);
        assertThat(aviso.inicio()).isEqualTo(OffsetDateTime.parse("2026-11-13T09:08:00-03:00"));
        assertThat(aviso.fim()).isEqualTo(OffsetDateTime.parse("2026-11-13T23:59:00-03:00"));
        assertThat(aviso.descricao()).startsWith("INMET publica aviso").contains("mm/h");
        assertThat(aviso.instrucao()).contains("199");
        assertThat(aviso.link()).isEqualTo("https://avisos.inmet.gov.br/56392");
    }

    @Test
    void avisoDaMesmaMesorregiaoQueNaoIncluiOMunicipioFicaDeFora() throws IOException {
        // o 56396 vale para Barcelos e Santa Isabel do Rio Negro, mas não para São Gabriel da Cachoeira
        String cap = ler("cap-56396-sem-sao-gabriel.xml");
        assertThat(cap).contains("Norte Amazonense").contains("Barcelos - AM");
        assertThat(InmetConversor.lerCap("56396", cap, SAO_GABRIEL)).isEmpty();
        assertThat(InmetConversor.lerCap("56396", cap, "1300409")).isPresent(); // Barcelos
    }

    @Test
    void converteOQueFoiGuardadoESeparaAtivosDeRecentes() throws IOException {
        ObjectNode guardado = new ObjectMapper().createObjectNode();
        ObjectNode caps = guardado.putObject("caps");
        caps.put("56392", ler("cap-56392-com-sao-gabriel.xml"));
        caps.put("56396", ler("cap-56396-sem-sao-gabriel.xml"));

        List<AvisoInmet> avisos = InmetConversor.converter(guardado.toString(), SAO_GABRIEL);
        assertThat(avisos).extracting(AvisoInmet::id).containsExactly("56392");

        // às 15h de 13/11 em Brasília o aviso está valendo; no dia 15 ele já terminou; no dia 25, passou dos 7 dias
        var durante = AvisosInmetServico.separar(avisos, Instant.parse("2026-11-13T18:00:00Z"), 7);
        assertThat(durante.ativos()).hasSize(1);
        assertThat(durante.recentes()).isEmpty();
        var depois = AvisosInmetServico.separar(avisos, Instant.parse("2026-11-15T12:00:00Z"), 7);
        assertThat(depois.ativos()).isEmpty();
        assertThat(depois.recentes()).hasSize(1);
        assertThat(AvisosInmetServico.separar(avisos, Instant.parse("2026-11-25T12:00:00Z"), 7).recentes()).isEmpty();
    }

    @Test
    void avisoRepublicadoComOutroNumeroApareceUmaVezSo() throws IOException {
        var original = InmetConversor.lerCap("56392", ler("cap-56392-com-sao-gabriel.xml"), SAO_GABRIEL).orElseThrow();
        var republicado = new AvisoInmet("56400", original.evento(), original.eventoCodigo(), original.grau(),
                original.inicio().minusHours(9), original.fim(), original.descricao(), original.instrucao(), original.link());
        var outroDia = new AvisoInmet("56300", original.evento(), original.eventoCodigo(), original.grau(),
                original.inicio().minusDays(1), original.fim().minusDays(1), original.descricao(), original.instrucao(), original.link());

        assertThat(InmetConversor.semRepetidos(List.of(original, republicado, outroDia)))
                .extracting(AvisoInmet::id).containsExactly("56400", "56300");
    }

    @Test
    void grauECodigoDoEvento() {
        assertThat(InmetConversor.grau("Severe")).isEqualTo(AvisoInmet.Grau.PERIGO);
        assertThat(InmetConversor.grau("Extreme")).isEqualTo(AvisoInmet.Grau.GRANDE_PERIGO);
        assertThat(InmetConversor.grau("Unknown")).isNull();
        assertThat(InmetConversor.codigoDoEvento("Declínio de Temperatura")).isEqualTo("DECLINIO_DE_TEMPERATURA");
        assertThat(InmetConversor.codigoDoEvento("Onda de Calor")).isEqualTo("ONDA_DE_CALOR");
    }
}
