package br.com.avisabrasil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Respostas reais das APIs, salvas em src/test/resources em 11/11/2026. */
public final class Fixtures {

    public static final String OPEN_METEO = "/open-meteo/previsao-sao-gabriel.json";
    public static final String GLOFAS_PREVISAO = "/glofas/previsao-sao-gabriel.json";
    /** Histórico real reduzido a outubro–dezembro de cada ano, para o arquivo ficar pequeno. */
    public static final String GLOFAS_HISTORICO = "/glofas/historico-out-dez.json";
    /** Resposta real do Overpass (raio de 8 km da sede), salva em 12/11/2026 (UTC). */
    public static final String OVERPASS = "/overpass/pontos-sao-gabriel.json";

    private Fixtures() {
    }

    public static String ler(String caminho) {
        try (InputStream in = Fixtures.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture não encontrada: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
