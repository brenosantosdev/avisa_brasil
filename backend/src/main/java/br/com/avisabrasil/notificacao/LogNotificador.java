package br.com.avisabrasil.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Registra o alerta no log do servidor. Sempre ligado, útil para conferir o texto. */
@Component
public class LogNotificador implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(LogNotificador.class);

    @Override
    public Canal canal() {
        return Canal.LOG;
    }

    @Override
    public boolean habilitado() {
        return true;
    }

    @Override
    public String motivoDesligado() {
        return null;
    }

    @Override
    public void enviar(MensagemAlerta mensagem) {
        log.info("ALERTA {}\n{}", mensagem.titulo(), mensagem.texto());
    }
}
