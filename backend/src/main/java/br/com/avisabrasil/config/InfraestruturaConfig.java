package br.com.avisabrasil.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Beans de infraestrutura: relógio no fuso da comunidade e cliente HTTP com tempo limite. */
@Configuration
@EnableConfigurationProperties(AvisaBrasilProperties.class)
public class InfraestruturaConfig {

    public static final ZoneId FUSO_COMUNIDADE = ZoneId.of("America/Manaus");
    public static final String USER_AGENT = "AvisaBrasil/0.1 (FIAP Global Solution 2026.2)";

    @Bean
    public Clock relogio() {
        return Clock.system(FUSO_COMUNIDADE);
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        return builder
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }
}
