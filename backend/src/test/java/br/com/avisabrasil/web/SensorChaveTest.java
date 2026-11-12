package br.com.avisabrasil.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Com SENSOR_API_KEY definida, só aceita leituras com a chave certa no cabeçalho X-API-Key. */
@SpringBootTest(properties = "avisabrasil.segredos.sensor-api-key=chave-de-teste")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class SensorChaveTest {

    private static final String LEITURA = "{\"nivelCm\": 900}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exigeAChaveCerta() throws Exception {
        mockMvc.perform(post("/api/sensores/SGC-01/leituras").contentType(MediaType.APPLICATION_JSON).content(LEITURA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sensores/SGC-01/leituras").contentType(MediaType.APPLICATION_JSON).content(LEITURA)
                        .header("X-API-Key", "chave-errada"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sensores/SGC-01/leituras").contentType(MediaType.APPLICATION_JSON).content(LEITURA)
                        .header("X-API-Key", "chave-de-teste"))
                .andExpect(status().isCreated());
    }
}
