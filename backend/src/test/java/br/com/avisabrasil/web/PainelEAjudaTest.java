package br.com.avisabrasil.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.avisabrasil.ajuda.PedidosAjudaServico;
import br.com.avisabrasil.dominio.PedidoAjuda;
import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Senha do painel, rotas protegidas, "Preciso de ajuda" (com reenvio da fila offline), atendido, alerta manual e SSE. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class PainelEAjudaTest {

    private static final String PEDIDO = """
            {"tipo": "PRECISO_DE_AJUDA", "pessoas": 4, "nome": "Ana", "telefone": "+55 97 99999-0000",
             "latitude": -0.131, "longitude": -67.088, "idCliente": "%s", "criadoNoCliente": "2026-11-13T08:00:00-04:00"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PedidoAjudaRepositorio pedidos;
    @Autowired
    private PedidosAjudaServico servico;

    @BeforeEach
    void limpar() {
        pedidos.deleteAll();
    }

    @Test
    void senhaDoPainel() throws Exception {
        mockMvc.perform(get("/api/painel/sessao"))
                .andExpect(jsonPath("$.autenticado").value(false))
                .andExpect(jsonPath("$.senhaConfigurada").value(true));
        mockMvc.perform(post("/api/painel/entrar").contentType(MediaType.APPLICATION_JSON).content("{\"senha\": \"errada\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession sessao = SessaoDoPainel.entrar(mockMvc);
        mockMvc.perform(get("/api/painel/sessao").session(sessao)).andExpect(jsonPath("$.autenticado").value(true));
        mockMvc.perform(get("/api/alertas/canais").session(sessao)).andExpect(status().isOk());

        mockMvc.perform(post("/api/painel/sair").session(sessao)).andExpect(status().isOk());
        mockMvc.perform(get("/api/alertas/canais").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    void rotasDaDefesaCivilExigemSenha() throws Exception {
        mockMvc.perform(get("/api/alertas/canais")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/alertas/teste")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/alertas/envios")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/simulacao/limpar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pedidos-ajuda")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/pedidos-ajuda/1/atendido")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/painel/eventos")).andExpect(status().isUnauthorized());
        // telas e dados públicos continuam livres
        mockMvc.perform(get("/api/risco")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pontos-seguros")).andExpect(status().isOk());
    }

    @Test
    void pedidoDeAjudaPublicoSemDuplicarOReenvioDaFilaOffline() throws Exception {
        String corpo = PEDIDO.formatted("celular-abc-123");
        String id = mockMvc.perform(post("/api/pedidos-ajuda").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                // para quem pediu, só a confirmação (sem os dados das outras pessoas)
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        id = String.valueOf(com.jayway.jsonpath.JsonPath.<Number>read(id, "$.id"));

        // a fila offline reenviou o mesmo pedido: não duplica
        mockMvc.perform(post("/api/pedidos-ajuda").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(id)));

        MockHttpSession painel = SessaoDoPainel.entrar(mockMvc);
        mockMvc.perform(get("/api/pedidos-ajuda").session(painel))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("PRECISO_DE_AJUDA"))
                .andExpect(jsonPath("$[0].canal").value("WEB"))
                .andExpect(jsonPath("$[0].pessoas").value(4))
                .andExpect(jsonPath("$[0].latitude").value(-0.131))
                .andExpect(jsonPath("$[0].criadoNoCliente").value(startsWith("2026-11-13T12:00:00")))
                .andExpect(jsonPath("$[0].atendido").value(false));

        mockMvc.perform(patch("/api/pedidos-ajuda/" + id + "/atendido").session(painel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atendido").value(true));
        mockMvc.perform(patch("/api/pedidos-ajuda/999999/atendido").session(painel)).andExpect(status().isNotFound());
    }

    @Test
    void pedidoInvalidoERecusado() throws Exception {
        mockMvc.perform(post("/api/pedidos-ajuda").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"PRECISO_DE_AJUDA\", \"pessoas\": 0, \"idCliente\": \"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/pedidos-ajuda").contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO.formatted("id com espaço <script>")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertaManualDaDefesaCivil() throws Exception {
        MockHttpSession painel = SessaoDoPainel.entrar(mockMvc);
        mockMvc.perform(post("/api/alertas/manual").session(painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivel\": \"ALERTA\", \"tipo\": \"CHEIA\", \"mensagem\": \"Barcos da Defesa Civil no porto às 15h.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento").value("MANUAL"))
                .andExpect(jsonPath("$.mensagem").value("Defesa Civil: Barcos da Defesa Civil no porto às 15h."));
        // sem dizer se é cheia ou seca, recusa
        mockMvc.perform(post("/api/alertas/manual").session(painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivel\": \"ALERTA\", \"mensagem\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventosAoVivoComSenha() throws Exception {
        mockMvc.perform(get("/api/painel/eventos").session(SessaoDoPainel.entrar(mockMvc)))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", startsWith("text/event-stream")));
    }

    @Test
    void avisoDePrivacidadeEPublicoEDizOPrazoDeGuarda() throws Exception {
        mockMvc.perform(get("/api/pedidos-ajuda/privacidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeRetencao").value(90));
    }

    @Test
    void pedidosMaisAntigosQueOPrazoSaoApagados() {
        pedidos.save(new PedidoAjuda(PedidoAjuda.Tipo.PRECISO_DE_AJUDA, PedidoAjuda.Canal.WEB, null, "antigo",
                Instant.now().minus(Duration.ofDays(91))));
        pedidos.save(new PedidoAjuda(PedidoAjuda.Tipo.ESTOU_BEM, PedidoAjuda.Canal.WEB, null, "recente",
                Instant.now().minus(Duration.ofDays(89))));

        assertThat(servico.apagarAntigos()).isEqualTo(1);
        assertThat(pedidos.findAll()).extracting(PedidoAjuda::getNome).containsExactly("recente");
    }

    @Test
    void limparSimulacaoSoApagaPedidosQuandoPedido() throws Exception {
        mockMvc.perform(post("/api/pedidos-ajuda").contentType(MediaType.APPLICATION_JSON).content(PEDIDO.formatted("teste-limpeza")))
                .andExpect(status().isCreated());
        MockHttpSession painel = SessaoDoPainel.entrar(mockMvc);

        // sem a opção, os pedidos ficam
        mockMvc.perform(post("/api/simulacao/limpar").session(painel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos").value(0));
        assertThat(pedidos.count()).isEqualTo(1);

        // com a opção "também apagar pedidos de ajuda", o banco fica limpo para a apresentação
        mockMvc.perform(post("/api/simulacao/limpar").param("pedidos", "true").session(painel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos").value(1));
        assertThat(pedidos.count()).isZero();
    }
}
