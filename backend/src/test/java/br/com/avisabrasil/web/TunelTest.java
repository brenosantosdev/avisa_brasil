package br.com.avisabrasil.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Na demo, os celulares chegam pelo túnel (Cloudflare Tunnel ou ngrok), e para o servidor todos vêm de
 * 127.0.0.1. Com o X-Forwarded-For, cada celular tem o seu próprio limite de envios de "Preciso de ajuda".
 * O teste usa o Tomcat de verdade (porta aleatória), porque é ele que lê o cabeçalho.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class TunelTest {

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private PedidoAjudaRepositorio pedidos;

    private HttpStatus enviar(String ipDoCelular, int numero) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Forwarded-For", ipDoCelular);
        String corpo = "{\"tipo\": \"ESTOU_BEM\", \"pessoas\": 1, \"idCliente\": \"tunel-" + ipDoCelular.replace('.', '-')
                + "-" + numero + "\"}";
        return HttpStatus.valueOf(http.postForEntity("/api/pedidos-ajuda", new HttpEntity<>(corpo, cabecalhos), String.class)
                .getStatusCode().value());
    }

    @Test
    void cadaCelularAtrasDoTunelTemOSeuProprioLimite() {
        pedidos.deleteAll();
        for (int i = 0; i < 20; i++) {
            assertThat(enviar("203.0.113.10", i)).isEqualTo(HttpStatus.CREATED);
        }
        // o 21º envio do mesmo celular passa do limite
        assertThat(enviar("203.0.113.10", 20)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // outro celular, pelo mesmo túnel, continua podendo pedir ajuda
        assertThat(enviar("203.0.113.20", 0)).isEqualTo(HttpStatus.CREATED);
        pedidos.deleteAll();
    }
}
