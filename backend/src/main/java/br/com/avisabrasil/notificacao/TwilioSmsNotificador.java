package br.com.avisabrasil.notificacao;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SMS pelo Twilio, só envio (não recebe respostas). Na conta de teste (trial) o Twilio só entrega
 * para números verificados. Precisa de TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM e
 * TWILIO_DESTINOS (números separados por vírgula, no formato +5592...).
 */
@Component
public class TwilioSmsNotificador implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsNotificador.class);
    /** Limite de segurança do texto (o Twilio divide em partes de 70 caracteres com acentos). */
    static final int MAXIMO_CARACTERES = 480;

    private final RestClient http;
    private final String sid;
    private final String token;
    private final String de;
    private final List<String> destinos;

    public TwilioSmsNotificador(RestClient http,
            @Value("${avisabrasil.segredos.twilio-account-sid:}") String sid,
            @Value("${avisabrasil.segredos.twilio-auth-token:}") String token,
            @Value("${avisabrasil.segredos.twilio-from:}") String de,
            @Value("${avisabrasil.segredos.twilio-destinos:}") String destinos) {
        this.http = http;
        this.sid = sid.trim();
        this.token = token.trim();
        this.de = de.trim();
        this.destinos = Arrays.stream(destinos.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public Canal canal() {
        return Canal.SMS;
    }

    @Override
    public boolean habilitado() {
        return !sid.isEmpty() && !token.isEmpty() && !de.isEmpty() && !destinos.isEmpty();
    }

    @Override
    public String motivoDesligado() {
        return habilitado() ? null : "defina TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM e TWILIO_DESTINOS";
    }

    @Override
    public void enviar(MensagemAlerta mensagem) {
        String autorizacao = "Basic " + Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        int falhas = 0;
        for (String para : destinos) {
            try {
                http.post().uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", sid)
                        .header(HttpHeaders.AUTHORIZATION, autorizacao)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formulario(de, para, mensagem))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                falhas++;
                log.warn("SMS: falha para um destino ({})", ErroSeguro.descrever(e));
            }
        }
        if (falhas == destinos.size()) {
            throw new IllegalStateException("SMS falhou para todos os " + falhas + " destinos");
        }
    }

    static MultiValueMap<String, String> formulario(String de, String para, MensagemAlerta m) {
        String texto = m.textoCurto();
        if (texto.length() > MAXIMO_CARACTERES) {
            texto = texto.substring(0, MAXIMO_CARACTERES - 1) + "…";
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", de);
        form.add("To", para);
        form.add("Body", texto);
        return form;
    }
}
