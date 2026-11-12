package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.risco.TipoRisco;
import br.com.avisabrasil.rio.Nivel;

/**
 * Mensagem pronta para os canais.
 *
 * @param titulo linha curta (título da notificação push)
 * @param texto texto completo: nível, o que está acontecendo, o que fazer e a fonte
 * @param textoCurto versão curta para SMS
 * @param assinatura identifica o conteúdo do alerta (evento, nível, tipo, simulado), para não repetir em sequência
 */
public record MensagemAlerta(
        String titulo,
        String texto,
        String textoCurto,
        Nivel nivel,
        TipoRisco tipo,
        boolean simulado,
        boolean teste,
        String assinatura) {

    /** Prioridade de 1 (mínima) a 5 (máxima), no padrão do ntfy. */
    public int prioridade() {
        if (teste) {
            return 3;
        }
        return switch (nivel) {
            case NORMAL -> 2;
            case ATENCAO -> 3;
            case ALERTA -> 4;
            case EMERGENCIA -> 5;
        };
    }
}
