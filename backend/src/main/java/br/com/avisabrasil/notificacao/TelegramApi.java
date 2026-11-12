package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.config.InfraestruturaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Chamadas à Bot API do Telegram. O token (TELEGRAM_BOT_TOKEN) faz parte da URL, então as exceções
 * deste cliente nunca devem ser registradas com getMessage(): use {@link ErroSeguro}.
 */
@Component
public class TelegramApi {

    /** Tempo de espera do long polling (o Telegram segura a conexão até chegar mensagem). */
    static final int ESPERA_SEGUNDOS = 50;

    private final RestClient http;
    private final String token;

    public TelegramApi(RestClient.Builder builder, @Value("${avisabrasil.segredos.telegram-bot-token:}") String token) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(ESPERA_SEGUNDOS + 20));
        this.http = builder.requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, InfraestruturaConfig.USER_AGENT)
                .build();
        this.token = token.trim();
    }

    public boolean habilitado() {
        return !token.isEmpty();
    }

    public void enviarTexto(long chatId, String texto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("chat_id", chatId);
        corpo.put("text", texto);
        http.post().uri("https://api.telegram.org/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toBodilessEntity();
    }

    /** Long polling: espera até {@value #ESPERA_SEGUNDOS} s por mensagens novas a partir de {@code offset}. */
    public JsonNode buscarAtualizacoes(long offset) {
        return http.get()
                .uri("https://api.telegram.org/bot{token}/getUpdates?timeout={t}&offset={o}&allowed_updates={a}",
                        token, ESPERA_SEGUNDOS, offset, "[\"message\"]")
                .retrieve()
                .body(JsonNode.class);
    }
}
