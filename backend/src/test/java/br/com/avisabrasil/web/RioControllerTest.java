package br.com.avisabrasil.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.integracoes.ana.AnaClient;
import br.com.avisabrasil.rio.HistoricoAnaServico;
import br.com.avisabrasil.rio.RioAgoraServico;
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

/** API "O rio agora" com a ANA simulada pelas respostas reais salvas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class RioControllerTest {

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioDoTeste() {
            // 11/11/2026 21:00 em Manaus, pouco depois da última leitura da fixture (19:45 em Manaus)
            return Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("America/Manaus"));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RioAgoraServico rio;
    @Autowired
    private HistoricoAnaServico historico;
    @MockitoBean
    private AnaClient ana;

    @BeforeAll
    static void limparCacheEmDisco() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of("target/cache-teste"));
    }

    @Test
    void nivelAtualTendenciaEHistorico() throws Exception {
        when(ana.habilitado()).thenReturn(true);
        when(ana.buscarTelemetria(anyInt(), any())).thenReturn(Fixtures.ler(Fixtures.ANA_TELEMETRIA));
        when(ana.buscarCotas(anyInt(), any(), any())).thenReturn(Fixtures.ler(Fixtures.ANA_COTAS_PRINCIPAL));
        rio.atualizar();
        historico.baixarProximoAno();

        mockMvc.perform(get("/api/rio/agora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value(AnaClient.FONTE))
                .andExpect(jsonPath("$.situacao").value("ATUALIZADO"))
                .andExpect(jsonPath("$.dados.estacao.codigo").value(14330000))
                .andExpect(jsonPath("$.dados.atual.cotaCm").value(774.0))
                .andExpect(jsonPath("$.dados.atual.dataHora").value("2026-11-11T19:45:00"))
                .andExpect(jsonPath("$.dados.variacao24hCm").value(-2.0))
                .andExpect(jsonPath("$.dados.velocidadeCmPorDia").value(-2.5))
                // só 2026 baixado: pouco histórico, então ainda não há cotas nem "normal"
                .andExpect(jsonPath("$.dados.cotas").doesNotExist())
                .andExpect(jsonPath("$.dados.historicoPrincipal.progresso.anosBaixados").value(1))
                .andExpect(jsonPath("$.dados.historicoPrincipal.progresso.anosTotal").value(2026 - 1977 + 1));
    }

    @Test
    void semCredenciaisAIntegracaoFicaDesligada() throws Exception {
        when(ana.habilitado()).thenReturn(false);
        rio.atualizarSeNecessario();

        verify(ana, never()).buscarTelemetria(anyInt(), any());
        mockMvc.perform(get("/api/rio/agora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erro").value("Integração com a ANA desligada: defina ANA_USUARIO e ANA_SENHA"));
    }
}
