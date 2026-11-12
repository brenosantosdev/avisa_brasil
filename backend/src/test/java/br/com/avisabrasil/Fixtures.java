package br.com.avisabrasil;

import br.com.avisabrasil.previsao.FaixaNormal;
import br.com.avisabrasil.rio.CotasDeReferencia;
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
    /** Respostas reais da ANA (11/11/2026). Não contêm token nem credenciais. */
    public static final String ANA_TELEMETRIA = "/ana/telemetria-14330000-7dias.json";
    public static final String ANA_SEM_REGISTROS = "/ana/telemetria-sem-registros.json";
    public static final String ANA_COTAS_CIDADE = "/ana/cotas-14320001-2026.json";
    public static final String ANA_COTAS_PRINCIPAL = "/ana/cotas-14330000-2026.json";

    /** Cotas provisórias reais de Curicuriari (11/2026): cheia p50/p67/p90, seca p50/p33/p10. */
    public static final CotasDeReferencia COTAS_CURICURIARI = new CotasDeReferencia(
            new CotasDeReferencia.Faixa(1385, 1417, 1473, CotasDeReferencia.Origem.PROVISORIA, "teste", 1978, 2025, 48),
            new CotasDeReferencia.Faixa(640, 589, 529, CotasDeReferencia.Origem.PROVISORIA, "teste", 1978, 2025, 48));
    /** Normal da ANA em Curicuriari para 11/11 (1978–2025): 5% abaixo de 723, 10% abaixo de 746, 25% abaixo de 821. */
    public static final FaixaNormal NORMAL_NOVEMBRO = new FaixaNormal(723, 746, 821, 874, 947, 1012, 1978, 2025, 7, 720);

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
