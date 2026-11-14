package br.com.avisabrasil.integracoes;

import br.com.avisabrasil.avisos.InmetConversor;
import br.com.avisabrasil.config.AvisaBrasilProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Busca os avisos meteorológicos do INMET (grátis, sem chave).
 * <ol>
 *   <li>Lê o RSS e escolhe os avisos da mesorregião da comunidade que não terminaram há mais de alguns dias.</li>
 *   <li>Abre o aviso completo (CAP) de cada um, que lista os municípios. Um aviso não muda depois de
 *       publicado, então cada CAP é baixado uma vez só (com pausa entre os pedidos).</li>
 * </ol>
 * O resultado é um JSON {"caps": {id: xml}}; quem decide se o aviso vale para o município é o
 * {@link InmetConversor}.
 */
@Component
public class InmetClient {

    public static final String FONTE = "INMET (Instituto Nacional de Meteorologia)";
    public static final String FONTE_URL = "https://avisos.inmet.gov.br/";

    private static final Logger log = LoggerFactory.getLogger(InmetClient.class);
    private static final Duration PAUSA_ENTRE_AVISOS = Duration.ofMillis(400);

    private final RestClient http;
    private final AvisaBrasilProperties.Inmet config;
    private final Clock relogio;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> capsBaixados = new ConcurrentHashMap<>();

    public InmetClient(RestClient http, AvisaBrasilProperties propriedades, Clock relogio) {
        this.http = http;
        this.config = propriedades.fontes().inmet();
        this.relogio = relogio;
    }

    public String buscarAvisos() {
        String rss = baixar(config.url());
        Instant desde = relogio.instant().minus(Duration.ofDays(config.diasRecentes()));
        List<String> ids = InmetConversor.candidatos(rss, config.mesorregiao(), desde);
        capsBaixados.keySet().retainAll(ids);

        ObjectNode resultado = json.createObjectNode();
        ObjectNode caps = resultado.putObject("caps");
        int novos = 0;
        for (String id : ids) {
            String cap = capsBaixados.get(id);
            if (cap == null) {
                if (novos++ > 0) {
                    pausa();
                }
                // o id vem do RSS, mas o endereço é montado aqui (só números), nunca copiado do conteúdo
                cap = baixar(config.url() + "/" + Long.parseLong(id));
                capsBaixados.put(id, cap);
            }
            caps.put(id, cap);
        }
        log.info("INMET: {} avisos da mesorregião {} nos últimos {} dias ({} baixados agora)",
                ids.size(), config.mesorregiao(), config.diasRecentes(), novos);
        return resultado.toString();
    }

    /** O INMET às vezes não informa o charset; o conteúdo é UTF-8. */
    private String baixar(String url) {
        byte[] corpo = http.get().uri(url).retrieve().body(byte[].class);
        return corpo == null ? "" : new String(corpo, StandardCharsets.UTF_8);
    }

    private static void pausa() {
        try {
            Thread.sleep(PAUSA_ENTRE_AVISOS.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido", e);
        }
    }
}
