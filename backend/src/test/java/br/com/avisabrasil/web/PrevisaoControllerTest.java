package br.com.avisabrasil.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.integracoes.GlofasClient;
import br.com.avisabrasil.integracoes.OpenMeteoClient;
import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.PrevisaoTempoServico;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.ResourceAccessException;

/** Testa a API de previsão de ponta a ponta, com as APIs externas simuladas pelas fixtures reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class PrevisaoControllerTest {

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioDoTeste() {
            return Clock.fixed(Instant.parse("2026-11-11T23:45:00Z"), ZoneId.of("America/Manaus"));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PrevisaoTempoServico tempo;
    @Autowired
    private PrevisaoRioServico rio;
    @MockitoBean
    private OpenMeteoClient openMeteo;
    @MockitoBean
    private GlofasClient glofas;

    @BeforeAll
    static void limparCacheEmDisco() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of("target/cache-teste"));
    }

    @Test
    void tempoComDadosReaisEFonte() throws Exception {
        when(openMeteo.buscarPrevisao()).thenReturn(Fixtures.ler(Fixtures.OPEN_METEO));
        tempo.atualizar();

        mockMvc.perform(get("/api/previsao/tempo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("Open-Meteo"))
                .andExpect(jsonPath("$.situacao").value("ATUALIZADO"))
                .andExpect(jsonPath("$.atualizadoEm").value("2026-11-11T19:45:00-04:00"))
                .andExpect(jsonPath("$.dados.dias.length()").value(7))
                .andExpect(jsonPath("$.dados.dias[1].categoriaChuva").value("FRACA"))
                .andExpect(jsonPath("$.dados.dias[1].periodoMaisChuvoso").value("TARDE"))
                .andExpect(jsonPath("$.dados.dias[1].chuvaPorPeriodo.TARDE").value(5.5));

        // a fonte cai: continua mostrando o último dado válido, marcado como desatualizado
        when(openMeteo.buscarPrevisao()).thenThrow(new ResourceAccessException("Tempo esgotado"));
        tempo.atualizar();

        mockMvc.perform(get("/api/previsao/tempo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("DESATUALIZADO"))
                .andExpect(jsonPath("$.dados.dias.length()").value(7));
    }

    @Test
    void rioComTendenciaENormalDaEpoca() throws Exception {
        when(glofas.buscarPrevisao()).thenReturn(Fixtures.ler(Fixtures.GLOFAS_PREVISAO));
        when(glofas.buscarHistorico(any())).thenReturn(Fixtures.ler(Fixtures.GLOFAS_HISTORICO));
        rio.atualizarPrevisao();
        rio.atualizarHistorico();

        mockMvc.perform(get("/api/previsao/rio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ATUALIZADO"))
                .andExpect(jsonPath("$.dados.rio").value("Rio Negro"))
                .andExpect(jsonPath("$.dados.hoje").value("2026-11-11"))
                .andExpect(jsonPath("$.dados.dias.length()").value(60))
                .andExpect(jsonPath("$.dados.resumo.tendencia").value("ESTAVEL"))
                .andExpect(jsonPath("$.dados.resumo.comparacaoHoje").value("MUITO_ABAIXO"))
                .andExpect(jsonPath("$.dados.historico.situacao").value("ATUALIZADO"));
    }

    @Test
    void comunidadeConfigurada() throws Exception {
        mockMvc.perform(get("/api/comunidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("São Gabriel da Cachoeira"))
                .andExpect(jsonPath("$.rio").value("Rio Negro"));
    }
}
