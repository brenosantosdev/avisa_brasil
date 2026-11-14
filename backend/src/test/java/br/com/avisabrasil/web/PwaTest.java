package br.com.avisabrasil.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Versão para celular (PWA): manifesto com ícones que existem e service worker que só guarda arquivos que
 * existem e dados públicos. Se um arquivo da lista faltar, o navegador não instala o service worker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PwaTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    @Autowired
    private MockMvc mockMvc;

    private static String sw() throws IOException {
        return Files.readString(STATIC.resolve("sw.js"), StandardCharsets.UTF_8);
    }

    /** Os textos entre aspas simples de uma lista do sw.js, por exemplo TELAS_SEM_CONEXAO = [...]. */
    private static List<String> lista(String codigo, String nome) {
        Matcher bloco = Pattern.compile(nome + "\\s*=\\s*[^\\[]*\\[([^\\]]*)]", Pattern.DOTALL).matcher(codigo);
        assertThat(bloco.find()).as("lista %s no sw.js", nome).isTrue();
        List<String> itens = new ArrayList<>();
        Matcher item = Pattern.compile("'([^']+)'").matcher(bloco.group(1));
        while (item.find()) {
            itens.add(item.group(1));
        }
        return itens;
    }

    @Test
    void manifestoInstalavelComIconesQueExistem() throws Exception {
        String corpo = mockMvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode manifesto = new ObjectMapper().readTree(corpo);

        assertThat(manifesto.get("display").asText()).isEqualTo("standalone");
        assertThat(manifesto.get("start_url").asText()).isEqualTo("/");
        assertThat(manifesto.get("name").asText()).contains("AvisaBrasil");
        // o Chrome exige ícones de 192 e 512 px para oferecer a instalação
        List<String> tamanhos = new ArrayList<>();
        for (JsonNode icone : manifesto.get("icons")) {
            tamanhos.add(icone.get("sizes").asText());
            mockMvc.perform(get(icone.get("src").asText())).andExpect(status().isOk());
        }
        assertThat(tamanhos).contains("192x192", "512x512");
        for (JsonNode atalho : manifesto.get("shortcuts")) {
            mockMvc.perform(get(atalho.get("url").asText())).andExpect(status().isOk());
        }
    }

    @Test
    void serviceWorkerNaRaizEArquivosGuardadosExistem() throws Exception {
        mockMvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));

        String codigo = sw();
        List<String> arquivos = new ArrayList<>(lista(codigo, "TELAS_SEM_CONEXAO"));
        arquivos.addAll(lista(codigo, "ARQUIVOS"));
        assertThat(arquivos).contains("/", "/ajuda.html", "/offline.html");
        for (String arquivo : arquivos) {
            mockMvc.perform(get(arquivo)).andExpect(status().isOk());
        }
    }

    @Test
    void serviceWorkerSoGuardaDadosPublicos() throws Exception {
        List<String> dados = lista(sw(), "DADOS_SEM_CONEXAO");
        assertThat(dados).containsExactlyInAnyOrder("/api/comunidade", "/api/risco", "/api/pedidos-ajuda/privacidade");
        // nenhum deles exige a senha do painel (dados da Defesa Civil e pedidos de outras pessoas nunca ficam no celular)
        for (String dado : dados) {
            mockMvc.perform(get(dado)).andExpect(status().isOk());
        }
    }

    @Test
    void todasAsTelasLigamOManifesto() throws IOException {
        try (var arquivos = Files.list(STATIC)) {
            for (Path tela : arquivos.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(tela, StandardCharsets.UTF_8);
                assertThat(html).as(tela.getFileName().toString())
                        .contains("rel=\"manifest\" href=\"/manifest.webmanifest\"")
                        .contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\"");
            }
        }
    }
}
