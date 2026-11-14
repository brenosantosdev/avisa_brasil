package br.com.avisabrasil.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Entra no painel com a senha do perfil de teste e devolve a sessão. */
final class SessaoDoPainel {

    static final String SENHA_DE_TESTE = "senha-de-teste";

    private SessaoDoPainel() {
    }

    static MockHttpSession entrar(MockMvc mockMvc) throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mockMvc.perform(post("/api/painel/entrar").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"" + SENHA_DE_TESTE + "\"}"))
                .andExpect(status().isOk());
        return sessao;
    }
}
