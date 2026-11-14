package br.com.avisabrasil.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contraste das cores (WCAG 2.1) nos quatro temas da tela Configurações: claro, escuro, alto contraste e alto
 * contraste escuro. A paleta é lida do próprio estilo.css. Texto precisa de 4,5:1 (nível AA); no alto contraste,
 * 7:1 (nível AAA). Ícones e bordas que não são texto precisam de 3:1.
 */
class ContrasteTest {

    private static final Path ESTILO = Path.of("src/main/resources/static/css/estilo.css");

    /** Combinações usadas nas telas: {cor do texto, cor do fundo, "texto" ou "grafico"}. */
    private static final String[][] PARES = {
            {"texto", "fundo", "texto"},
            {"texto", "superficie", "texto"},
            {"texto", "superficie-2", "texto"},
            {"texto-suave", "superficie", "texto"},
            {"texto-suave", "fundo", "texto"},
            {"texto-suave", "superficie-2", "texto"},
            {"rio-texto", "superficie", "texto"},
            {"rio-texto", "fundo", "texto"},
            {"rio-texto", "rio-claro", "texto"},
            {"texto", "rio-claro", "texto"},
            {"sobre-cor", "rio", "texto"},
            {"sobre-cor", "normal", "texto"},
            {"sobre-cor", "alerta", "texto"},
            {"sobre-cor", "emergencia", "texto"},
            {"sobre-atencao", "atencao", "texto"},
            {"texto", "amarelo-suave", "texto"},
            {"texto", "amarelo-suave-2", "texto"},
            {"texto", "vermelho-suave", "texto"},
            {"verde-texto", "verde-suave", "texto"},
            {"verde-texto", "superficie", "texto"},
            {"vermelho-texto", "superficie", "texto"},
            {"laranja-texto", "laranja-suave", "texto"},
            {"marrom-texto", "superficie", "texto"},
            {"normal-texto", "superficie", "texto"},
            {"sobre-faixa", "faixa", "texto"},
            {"alerta-texto", "superficie", "grafico"},
            {"amarelo-borda", "amarelo-suave", "grafico"},
            {"borda", "superficie", "grafico-alto"},
    };

    private static Map<String, String> bloco(String css, String seletor) {
        Matcher m = Pattern.compile(Pattern.quote(seletor) + "\\s*\\{([^}]*)}").matcher(css);
        assertThat(m.find()).as("bloco %s no estilo.css", seletor).isTrue();
        Map<String, String> cores = new LinkedHashMap<>();
        Matcher v = Pattern.compile("--([\\w-]+):\\s*(#[0-9A-Fa-f]{6})\\s*;").matcher(m.group(1));
        while (v.find()) {
            cores.put(v.group(1), v.group(2));
        }
        return cores;
    }

    /** As cores de cada tema, na mesma ordem em que o CSS aplica os blocos. */
    private static Map<String, String> tema(String nome) throws IOException {
        String css = Files.readString(ESTILO, StandardCharsets.UTF_8);
        Map<String, String> cores = new LinkedHashMap<>(bloco(css, ":root"));
        if (nome.contains("escuro")) {
            cores.putAll(bloco(css, ":root[data-tema=\"escuro\"]"));
        }
        if (nome.startsWith("alto")) {
            cores.putAll(bloco(css, ":root[data-contraste=\"alto\"]"));
        }
        if (nome.equals("alto-escuro")) {
            cores.putAll(bloco(css, ":root[data-contraste=\"alto\"][data-tema=\"escuro\"]"));
        }
        return cores;
    }

    static double luminancia(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double s = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contraste(String a, String b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @ParameterizedTest
    @ValueSource(strings = {"claro", "escuro", "alto", "alto-escuro"})
    void coresDoTemaTemContrasteSuficiente(String nome) throws IOException {
        Map<String, String> cores = tema(nome);
        boolean alto = nome.startsWith("alto");
        List<String> falhas = new ArrayList<>();
        for (String[] par : PARES) {
            if (par[2].equals("grafico-alto") && !alto) {
                continue; // a borda fina dos cartões só precisa aparecer forte no alto contraste
            }
            String frente = cores.get(par[0]);
            String fundo = cores.get(par[1]);
            assertThat(frente).as("--%s no tema %s", par[0], nome).isNotNull();
            assertThat(fundo).as("--%s no tema %s", par[1], nome).isNotNull();
            double minimo = par[2].startsWith("grafico") ? (alto ? 4.5 : 3.0) : (alto ? 7.0 : 4.5);
            double c = contraste(frente, fundo);
            if (c < minimo) {
                falhas.add(String.format("%s (%s) sobre %s (%s): %.2f:1, mínimo %.1f:1", par[0], frente, par[1], fundo, c, minimo));
            }
        }
        assertThat(falhas).as("tema %s", nome).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"claro", "escuro", "alto", "alto-escuro"})
    void textoBrancoDoTopoContrasta(String nome) throws IOException {
        assertThat(contraste("#FFFFFF", tema(nome).get("topo"))).isGreaterThanOrEqualTo(nome.startsWith("alto") ? 7.0 : 4.5);
    }

    /**
     * O amarelo da Atenção é igual em todos os temas, então o texto em cima dele usa sempre --sobre-atencao
     * (escuro). Com --texto, no tema escuro o texto ficaria claro sobre amarelo (o erro achado na auditoria).
     */
    @org.junit.jupiter.api.Test
    void textoSobreOAmareloDaAtencaoUsaACorPropria() throws IOException {
        Pattern errado = Pattern.compile("background:\\s*var\\(--atencao\\);\\s*color:\\s*var\\(--texto\\)");
        try (var arquivos = Files.list(ESTILO.getParent())) {
            for (Path css : arquivos.filter(f -> f.toString().endsWith(".css")).toList()) {
                assertThat(errado.matcher(Files.readString(css, StandardCharsets.UTF_8)).find())
                        .as(css.getFileName() + ": texto sobre o amarelo da Atenção deve usar --sobre-atencao").isFalse();
            }
        }
    }

    @org.junit.jupiter.api.Test
    void formulaDoWcag() {
        assertThat(contraste("#000000", "#FFFFFF")).isEqualTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(contraste("#777777", "#FFFFFF")).isEqualTo(4.48, org.assertj.core.data.Offset.offset(0.01));
    }
}
