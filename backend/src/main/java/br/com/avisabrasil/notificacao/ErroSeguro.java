package br.com.avisabrasil.notificacao;

import org.springframework.web.client.RestClientResponseException;

/**
 * Descreve um erro sem vazar segredos. As mensagens do cliente HTTP trazem a URL completa, e no
 * Telegram o token do bot faz parte da URL; por isso só o tipo do erro e o código HTTP são usados.
 */
public final class ErroSeguro {

    private ErroSeguro() {
    }

    public static String descrever(Exception e) {
        if (e instanceof RestClientResponseException r) {
            return "HTTP " + r.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }

    /** Exceção com mensagem já segura, para registrar no histórico de envios. */
    public static IllegalStateException falha(String canal, Exception e) {
        return new IllegalStateException(canal + " falhou (" + descrever(e) + ")");
    }
}
