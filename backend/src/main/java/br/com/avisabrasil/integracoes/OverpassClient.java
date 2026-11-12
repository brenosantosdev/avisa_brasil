package br.com.avisabrasil.integracoes;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.config.InfraestruturaConfig;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Busca pontos de apoio reais (escolas, unidades de saúde, prédios públicos, abrigos) no
 * OpenStreetMap pela Overpass API. Uma consulta por dia, para respeitar a política de uso.
 * Se um servidor falhar, tenta o próximo da lista.
 */
@Component
public class OverpassClient {

    public static final String FONTE = "OpenStreetMap (Overpass API)";
    public static final String FONTE_URL = "https://www.openstreetmap.org/copyright";

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(90);

    private final RestClient http;
    private final AvisaBrasilProperties propriedades;

    /**
     * Cliente próprio, com tempo limite maior: em horário de pico a fila do Overpass passa
     * de 30 segundos (medido: 37 s em 12/11/2026).
     */
    public OverpassClient(RestClient.Builder builder, AvisaBrasilProperties propriedades) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(TEMPO_LIMITE);
        this.http = builder
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, InfraestruturaConfig.USER_AGENT)
                .build();
        this.propriedades = propriedades;
    }

    public String buscarPontos() {
        // corpo como texto já codificado, para ir com Content-Length: com o corpo em fluxo
        // (formulário do Spring) o overpass-api.de não responde por HTTP/1.1
        String corpo = "data=" + URLEncoder.encode(montarConsulta(), StandardCharsets.UTF_8);
        List<String> servidores = propriedades.fontes().overpass().servidores();
        RestClientException ultimoErro = null;
        for (String servidor : servidores) {
            try {
                return http.post().uri(servidor)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(corpo)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                log.warn("Overpass {} falhou: {}", servidor, e.getMessage());
                ultimoErro = e;
            }
        }
        throw ultimoErro != null ? ultimoErro : new IllegalStateException("Nenhum servidor Overpass configurado");
    }

    /** Consulta em Overpass QL: pontos num raio em volta da sede, com o centro das áreas. */
    String montarConsulta() {
        var c = propriedades.comunidade();
        String em = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)",
                propriedades.fontes().overpass().raioMetros(), c.latitude(), c.longitude());
        return "[out:json][timeout:25];("
                + "nwr[\"amenity\"~\"^(school|college|university|kindergarten|hospital|clinic|doctors"
                + "|community_centre|townhall|police|fire_station)$\"]" + em + ";"
                + "nwr[\"healthcare\"~\"^(hospital|clinic|centre|doctor)$\"]" + em + ";"
                + "nwr[\"emergency\"~\"^(assembly_point|shelter)$\"]" + em + ";"
                + "nwr[\"social_facility\"=\"shelter\"]" + em + ";"
                + ");out center tags;";
    }
}
