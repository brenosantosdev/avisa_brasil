package br.com.avisabrasil.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.avisabrasil.Fixtures;
import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.integracoes.Situacao;
import br.com.avisabrasil.rio.RioAgora;
import br.com.avisabrasil.rio.RioAgoraServico;
import java.time.OffsetDateTime;
import java.util.List;
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

/**
 * Motor de risco de ponta a ponta com o sensor simulado: alerta só quando o nível sobe e
 * "situação normalizada" quando volta ao Normal. A ANA é simulada com as cotas reais de Curicuriari.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class MotorDeRiscoTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RioAgoraServico rioAgora;

    @BeforeEach
    void anaSemLeituraMasComCotas() {
        var estacao = new AvisaBrasilProperties.Estacao(14330000, "Curicuriari", "Rio Negro", 1977, "teste");
        var rio = new RioAgora(estacao, null, null, null, List.of(), Fixtures.COTAS_CURICURIARI,
                null, Fixtures.NORMAL_NOVEMBRO, null, null, false, null, null);
        when(rioAgora.consultar()).thenReturn(new RespostaComFonte<>("ANA", "", null, Situacao.ATUALIZADO, null, rio));
    }

    /** Leituras espaçadas de 10 dias, para não haver tendência (preventivo) entre elas. */
    private void leitura(double cm, int dia) throws Exception {
        String dataHora = OffsetDateTime.parse("2026-11-11T08:00:00-04:00").plusDays(10L * dia).toString();
        mockMvc.perform(post("/api/sensores/SGC-01/leituras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelCm\": " + cm + ", \"dataHora\": \"" + dataHora + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void alertaSoQuandoSobeENormalizadaQuandoVolta() throws Exception {
        leitura(900, 0);   // normal
        mockMvc.perform(get("/api/risco")).andExpect(jsonPath("$.nivel").value("NORMAL"));

        leitura(1390, 1);  // Atenção de cheia → alerta
        leitura(1395, 2);  // continua Atenção → sem alerta
        leitura(1420, 3);  // Alerta → alerta
        leitura(1400, 4);  // desceu para Atenção → sem alerta
        mockMvc.perform(get("/api/risco"))
                .andExpect(jsonPath("$.nivel").value("ATENCAO"))
                .andExpect(jsonPath("$.tipo").value("CHEIA"))
                .andExpect(jsonPath("$.simulado").value(true))
                .andExpect(jsonPath("$.principal.fonte").value("SENSOR"))
                .andExpect(jsonPath("$.sensorInfo.ativo").value(true));

        leitura(900, 5);   // voltou ao normal → "situação normalizada"
        leitura(700, 6);   // seca pela época (abaixo dos 5% mais baixos) → alerta de seca

        mockMvc.perform(get("/api/risco/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].evento").value("NIVEL_SUBIU"))
                .andExpect(jsonPath("$[0].nivel").value("EMERGENCIA"))
                .andExpect(jsonPath("$[0].tipo").value("SECA"))
                .andExpect(jsonPath("$[1].evento").value("NORMALIZADO"))
                .andExpect(jsonPath("$[2].nivel").value("ALERTA"))
                .andExpect(jsonPath("$[2].mensagem").value(org.hamcrest.Matchers.startsWith("[SIMULAÇÃO] ALERTA – CHEIA.")))
                .andExpect(jsonPath("$[3].nivel").value("ATENCAO"));
    }

    @Test
    void leituraInvalidaERecusada() throws Exception {
        mockMvc.perform(post("/api/sensores/SGC-01/leituras").contentType(MediaType.APPLICATION_JSON).content("{\"nivelCm\": -5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sensores/NAO-EXISTE/leituras").contentType(MediaType.APPLICATION_JSON).content("{\"nivelCm\": 100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void referenciasParaOSimulador() throws Exception {
        mockMvc.perform(get("/api/sensores/SGC-01/simulacao"))
                // sem leitura da ANA ainda: o simulador avisa que precisa esperar
                .andExpect(status().isServiceUnavailable());
    }
}
