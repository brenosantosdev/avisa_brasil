package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.dominio.InscritoTelegram;
import br.com.avisabrasil.repositorio.InscritoTelegramRepositorio;
import br.com.avisabrasil.rio.Nivel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia o alerta em texto para todas as conversas inscritas no bot (/start). A pessoa responde
 * 1 = estou bem / 2 = preciso de ajuda, e o {@link TelegramBot} registra a resposta.
 */
@Component
public class TelegramNotificador implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificador.class);
    static final String PEDIDO_DE_RESPOSTA = "Responda: 1 = estou bem / 2 = preciso de ajuda";

    private final TelegramApi api;
    private final InscritoTelegramRepositorio inscritos;

    public TelegramNotificador(TelegramApi api, InscritoTelegramRepositorio inscritos) {
        this.api = api;
        this.inscritos = inscritos;
    }

    @Override
    public Canal canal() {
        return Canal.TELEGRAM;
    }

    @Override
    public boolean habilitado() {
        return api.habilitado();
    }

    @Override
    public String motivoDesligado() {
        return habilitado() ? null : "defina a variável TELEGRAM_BOT_TOKEN";
    }

    @Override
    @Transactional
    public void enviar(MensagemAlerta mensagem) {
        List<InscritoTelegram> destinos = inscritos.findByAtivoTrue();
        if (destinos.isEmpty()) {
            throw new IllegalStateException("ninguém inscrito no bot ainda (envie /start para o bot)");
        }
        String texto = textoComResposta(mensagem);
        int enviados = 0;
        for (InscritoTelegram inscrito : destinos) {
            try {
                api.enviarTexto(inscrito.getChatId(), texto);
                enviados++;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    inscrito.desativar(); // a pessoa bloqueou o bot ou saiu do grupo
                }
                log.warn("Telegram: falha para uma conversa ({})", ErroSeguro.descrever(e));
            } catch (RestClientException e) {
                log.warn("Telegram: falha para uma conversa ({})", ErroSeguro.descrever(e));
            }
        }
        if (enviados == 0) {
            throw new IllegalStateException("Telegram falhou para todas as " + destinos.size() + " conversas");
        }
    }

    /** Alertas (não testes nem "normalizada") pedem a resposta 1 ou 2. */
    static String textoComResposta(MensagemAlerta m) {
        return m.teste() || m.nivel() == Nivel.NORMAL ? m.texto() : m.texto() + "\n\n" + PEDIDO_DE_RESPOSTA;
    }
}
