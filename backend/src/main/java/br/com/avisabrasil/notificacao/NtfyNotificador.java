package br.com.avisabrasil.notificacao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Notificação push pelo ntfy (https://ntfy.sh): grátis e sem cadastro. Quem instala o app ntfy no
 * celular e assina o tópico recebe os alertas. O tópico vem de NTFY_TOPICO; use um nome difícil
 * de adivinhar, porque qualquer pessoa que souber o nome consegue ler e publicar.
 */
@Component
public class NtfyNotificador implements Notificador {

    private final RestClient http;
    private final String servidor;
    private final String topico;

    public NtfyNotificador(RestClient http, @Value("${avisabrasil.segredos.ntfy-servidor:https://ntfy.sh}") String servidor,
            @Value("${avisabrasil.segredos.ntfy-topico:}") String topico) {
        this.http = http;
        this.servidor = servidor.replaceAll("/+$", "");
        this.topico = topico.trim();
    }

    @Override
    public Canal canal() {
        return Canal.NTFY;
    }

    @Override
    public boolean habilitado() {
        return !topico.isEmpty();
    }

    @Override
    public String motivoDesligado() {
        return habilitado() ? null : "defina a variável NTFY_TOPICO";
    }

    @Override
    public void enviar(MensagemAlerta mensagem) {
        try {
            // publicação em JSON: aceita acentos e emojis no título (cabeçalhos HTTP não aceitam)
            http.post().uri(servidor + "/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo(topico, mensagem))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw ErroSeguro.falha("ntfy", e);
        }
    }

    static Map<String, Object> corpo(String topico, MensagemAlerta m) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("topic", topico);
        corpo.put("title", m.titulo());
        corpo.put("message", m.texto());
        corpo.put("priority", m.prioridade());
        corpo.put("tags", m.teste() ? List.of("test_tube") : m.simulado() ? List.of("warning", "construction") : List.of("warning"));
        return corpo;
    }
}
