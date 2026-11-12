package br.com.avisabrasil.notificacao;

/**
 * Um canal de envio. Cada canal se desliga sozinho quando a sua variável de ambiente não existe.
 * Implementações nunca colocam tokens ou senhas em log ou em mensagens de erro.
 */
public interface Notificador {

    Canal canal();

    boolean habilitado();

    /** Por que está desligado (por exemplo, "defina NTFY_TOPICO"); nulo quando ligado. */
    String motivoDesligado();

    /** Envia a mensagem. Lança exceção (sem segredos na mensagem) se falhar. */
    void enviar(MensagemAlerta mensagem);
}
