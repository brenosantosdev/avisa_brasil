package br.com.avisabrasil.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.integracoes.OverpassClient;
import br.com.avisabrasil.mapa.MapaServico;
import br.com.avisabrasil.repositorio.PontoSeguroRepositorio;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class MapaControllerTest {

    /** Ponto dentro do setor SR_005 (risco Muito Alto) da setorização do SGB. */
    private static final String PONTO_EM_SETOR_DE_RISCO = """
            {"nome":"Ginásio de teste","tipo":"ABRIGO","latitude":-0.128448,"longitude":-67.086051,
             "observacao":"Somente para teste","capacidade":150}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MapaServico mapa;
    @Autowired
    private PontoSeguroRepositorio repositorio;
    @MockitoBean
    private OverpassClient overpass;

    @BeforeAll
    static void limparCacheEmDisco() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of("target/cache-teste"));
    }

    @BeforeEach
    void preparar() {
        repositorio.deleteAll();
        when(overpass.buscarPontos()).thenReturn(Fixtures.ler(Fixtures.OVERPASS));
        mapa.atualizar();
    }

    @Test
    void pontosDoOsmComFonteEDataDaBase() throws Exception {
        mockMvc.perform(get("/api/mapa/pontos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("OpenStreetMap (Overpass API)"))
                .andExpect(jsonPath("$.situacao").value("ATUALIZADO"))
                .andExpect(jsonPath("$.dados.dadosOsmDe").value("2026-11-11T20:05:18-04:00"))
                .andExpect(jsonPath("$.dados.totalOpenStreetMap").value(13))
                .andExpect(jsonPath("$.dados.totalDefesaCivil").value(0))
                // ordenados por distância: a escola mais perto da sede fica a 236 m
                .andExpect(jsonPath("$.dados.pontos[0].id").value("osm:way/1531755836"))
                .andExpect(jsonPath("$.dados.pontos[0].distanciaDaSedeMetros").value(236))
                .andExpect(jsonPath("$.dados.pontos[0].origem").value("OPENSTREETMAP"));
    }

    @Test
    void cadastrarPontoSeguroAvisaSeFicaEmSetorDeRisco() throws Exception {
        mockMvc.perform(post("/api/pontos-seguros").contentType(MediaType.APPLICATION_JSON).content(PONTO_EM_SETOR_DE_RISCO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.origem").value("DEFESA_CIVIL"))
                .andExpect(jsonPath("$.setorDeRisco").value("SR_005"))
                .andExpect(jsonPath("$.grauRiscoSetor").value("Muito Alto"))
                .andExpect(jsonPath("$.capacidade").value(150));

        // pontos da Defesa Civil aparecem antes dos do OSM
        mockMvc.perform(get("/api/mapa/pontos"))
                .andExpect(jsonPath("$.dados.totalDefesaCivil").value(1))
                .andExpect(jsonPath("$.dados.pontos[0].nome").value("Ginásio de teste"))
                .andExpect(jsonPath("$.dados.pontos.length()").value(14));
    }

    @Test
    void recusarCadastroInvalido() throws Exception {
        mockMvc.perform(post("/api/pontos-seguros").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \",\"tipo\":\"ABRIGO\",\"latitude\":120,\"longitude\":-67}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/pontos-seguros").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"tipo\":\"CASTELO\",\"latitude\":0,\"longitude\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removerTiraDoMapaMasGuardaORegistro() throws Exception {
        String location = mockMvc.perform(post("/api/pontos-seguros")
                        .contentType(MediaType.APPLICATION_JSON).content(PONTO_EM_SETOR_DE_RISCO))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pontos-seguros")).andExpect(jsonPath("$.length()").value(0));
        org.assertj.core.api.Assertions.assertThat(repositorio.count()).isEqualTo(1);
    }

    @Test
    void osmForaDoArContinuaMostrandoPontosDaDefesaCivil() throws Exception {
        mockMvc.perform(post("/api/pontos-seguros").contentType(MediaType.APPLICATION_JSON).content(PONTO_EM_SETOR_DE_RISCO));
        when(overpass.buscarPontos()).thenThrow(new IllegalStateException("Overpass fora do ar"));
        mapa.atualizar();

        mockMvc.perform(get("/api/mapa/pontos"))
                .andExpect(jsonPath("$.situacao").value("DESATUALIZADO"))
                .andExpect(jsonPath("$.dados.totalDefesaCivil").value(1))
                .andExpect(jsonPath("$.dados.totalOpenStreetMap").value(13));
    }
}
