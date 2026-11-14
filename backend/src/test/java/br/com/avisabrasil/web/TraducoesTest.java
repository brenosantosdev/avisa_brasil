package br.com.avisabrasil.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Os arquivos de tradução precisam estar completos: as mesmas chaves do português, listas do mesmo
 * tamanho e as mesmas {variáveis}. Assim nenhuma tela mostra uma chave crua ou perde um número.
 */
class TraducoesTest {

    private static final Pattern VARIAVEL = Pattern.compile("\\{(\\w+)}");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path PASTA_JS = Path.of("src/main/resources/static/js");

    private static JsonNode ler(String idioma) throws IOException {
        try (InputStream entrada = TraducoesTest.class.getResourceAsStream("/static/i18n/" + idioma + ".json")) {
            assertThat(entrada).as("i18n/%s.json", idioma).isNotNull();
            return JSON.readTree(entrada);
        }
    }

    private static Set<String> variaveis(JsonNode valor) {
        Set<String> nomes = new TreeSet<>();
        List<JsonNode> textos = new ArrayList<>();
        if (valor.isArray()) {
            valor.forEach(textos::add);
        } else {
            textos.add(valor);
        }
        for (JsonNode texto : textos) {
            Matcher m = VARIAVEL.matcher(texto.asText());
            while (m.find()) {
                nomes.add(m.group(1));
            }
        }
        return nomes;
    }

    private static Set<String> chaves(JsonNode raiz) {
        Set<String> chaves = new TreeSet<>();
        raiz.fieldNames().forEachRemaining(chaves::add);
        return chaves;
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "en"})
    void traducaoCompletaEComAsMesmasVariaveis(String idioma) throws IOException {
        JsonNode pt = ler("pt");
        JsonNode outro = ler(idioma);

        assertThat(chaves(outro)).as("chaves de %s", idioma).isEqualTo(chaves(pt));
        for (Iterator<String> it = pt.fieldNames(); it.hasNext();) {
            String chave = it.next();
            JsonNode original = pt.get(chave);
            JsonNode traduzido = outro.get(chave);
            assertThat(traduzido.getNodeType()).as("%s em %s", chave, idioma).isEqualTo(original.getNodeType());
            if (original.isArray()) {
                assertThat(traduzido.size()).as("tamanho da lista %s em %s", chave, idioma).isEqualTo(original.size());
            }
            if (!chave.equals("_idioma")) {
                assertThat(variaveis(traduzido)).as("variáveis de %s em %s", chave, idioma).isEqualTo(variaveis(original));
                assertThat(traduzido.isArray() || !traduzido.asText().isBlank()).as("%s vazio em %s", chave, idioma).isTrue();
            }
        }
    }

    @Test
    void cadaIdiomaDizSeuCodigoParaAVozEOsNumeros() throws IOException {
        assertThat(ler("pt").get("_idioma").asText()).isEqualTo("pt-BR");
        assertThat(ler("es").get("_idioma").asText()).startsWith("es");
        assertThat(ler("en").get("_idioma").asText()).startsWith("en");
    }

    /** Toda chave usada no código das telas com t('...') existe no português (e portanto nos outros). */
    @Test
    void chavesUsadasNasTelasExistem() throws IOException {
        Set<String> conhecidas = chaves(ler("pt"));
        Pattern chamada = Pattern.compile("\\bt\\('([\\w.]+)'");
        Set<String> faltando = new TreeSet<>();
        try (Stream<Path> arquivos = Files.list(PASTA_JS)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".js")).toList()) {
                Matcher m = chamada.matcher(Files.readString(arquivo, StandardCharsets.UTF_8));
                while (m.find()) {
                    String chave = m.group(1);
                    if (!chave.endsWith(".") && !conhecidas.contains(chave)) {
                        faltando.add(arquivo.getFileName() + ": " + chave);
                    }
                }
            }
        }
        assertThat(faltando).isEmpty();
    }
}
