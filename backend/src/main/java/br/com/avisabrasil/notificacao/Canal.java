package br.com.avisabrasil.notificacao;

/** Canais de envio de alertas. */
public enum Canal {
    /** Registro no log do servidor (sempre ligado). */
    LOG,
    /** Notificação push no celular pelo ntfy (demo ao vivo). */
    NTFY,
    /** Mensagem no Telegram para quem se inscreveu no bot. */
    TELEGRAM,
    /** SMS pelo Twilio (só envio). */
    SMS
}
