package br.com.avisabrasil.integracoes.ana;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.config.InfraestruturaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Cliente do HidroWebService da ANA. Usuário e senha vêm só de ANA_USUARIO e ANA_SENHA e nunca
 * aparecem em log, exceção ou arquivo. Sem as variáveis, {@link #habilitado()} é falso.
 */
@Component
public class AnaClient {

    public static final String FONTE = "ANA – Agência Nacional de Águas (HidroWebService)";
    public static final String FONTE_URL = "https://www.snirh.gov.br/hidroweb/";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient http;
    private final AvisaBrasilProperties.Ana config;
    private final String usuario;
    private final String senha;
    private final SessaoAna sessao;

    public AnaClient(RestClient.Builder builder, AvisaBrasilProperties propriedades, Clock relogio,
            @Value("${avisabrasil.segredos.ana-usuario:}") String usuario, @Value("${avisabrasil.segredos.ana-senha:}") String senha) {
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(90));
        this.http = builder.requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, InfraestruturaConfig.USER_AGENT)
                .build();
        this.config = propriedades.ana();
        this.usuario = usuario;
        this.senha = senha;
        this.sessao = new SessaoAna(config.validadeToken(), config.intervaloMinimoLogin(),
                propriedades.fontes().pastaCache().resolve("ana-sessao.json"), relogio);
    }

    /** Falso quando ANA_USUARIO ou ANA_SENHA não estão definidas: a integração fica desligada. */
    public boolean habilitado() {
        return !usuario.isBlank() && !senha.isBlank();
    }

    /** Leituras telemétricas (cota, vazão e chuva a cada 15 min) dos últimos 7 dias até {@code dia}. */
    public String buscarTelemetria(int estacao, LocalDate dia) {
        URI uri = UriComponentsBuilder.fromUriString(config.url() + "/HidroinfoanaSerieTelemetricaAdotada/v1")
                .queryParam("Código da Estação", estacao)
                .queryParam("Tipo Filtro Data", "DATA_LEITURA")
                .queryParam("Data de Busca (yyyy-MM-dd)", dia)
                .queryParam("Range Intervalo de busca", "DIAS_7")
                .build().encode().toUri();
        return consultar(uri);
    }

    /** Cotas da régua convencional (leituras manuais), de {@code inicio} a {@code fim} (até 366 dias). */
    public String buscarCotas(int estacao, LocalDate inicio, LocalDate fim) {
        URI uri = UriComponentsBuilder.fromUriString(config.url() + "/HidroSerieCotas/v1")
                .queryParam("Código da Estação", estacao)
                .queryParam("Tipo Filtro Data", "DATA_LEITURA")
                .queryParam("Data Inicial (yyyy-MM-dd)", inicio)
                .queryParam("Data Final (yyyy-MM-dd)", fim)
                .build().encode().toUri();
        return consultar(uri);
    }

    public SessaoAna sessao() {
        return sessao;
    }

    private String consultar(URI uri) {
        if (!habilitado()) {
            throw new IllegalStateException("Integração com a ANA desligada: defina ANA_USUARIO e ANA_SENHA");
        }
        String token = sessao.token(this::login);
        try {
            return http.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                sessao.invalidar();
            }
            // só o código HTTP: o corpo da resposta não é repassado
            throw new IllegalStateException("ANA respondeu HTTP " + e.getStatusCode().value());
        }
    }

    /** Login: GET com os cabeçalhos Identificador e Senha. Devolve o JWT de "tokenautenticacao". */
    private String login() {
        String corpo;
        try {
            corpo = http.get().uri(config.url() + "/OAUth/v1")
                    .header("Identificador", usuario)
                    .header("Senha", senha)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Login na ANA recusado (HTTP " + e.getStatusCode().value() + ")");
        }
        return extrairToken(corpo);
    }

    /** O campo "token" da resposta é outro identificador; o JWT aceito pela API é "tokenautenticacao". */
    static String extrairToken(String corpo) {
        try {
            JsonNode itens = MAPPER.readTree(corpo).path("items");
            String token = itens.path("tokenautenticacao").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("Login na ANA sem token na resposta");
            }
            return token;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Resposta de login da ANA não é JSON");
        }
    }
}
