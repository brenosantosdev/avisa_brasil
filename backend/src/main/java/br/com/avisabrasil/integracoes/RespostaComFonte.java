package br.com.avisabrasil.integracoes;

import java.time.OffsetDateTime;

/**
 * Envelope de toda informação externa exibida: diz de onde veio, quando foi atualizada
 * e se está desatualizada.
 */
public record RespostaComFonte<T>(
        String fonte,
        String fonteUrl,
        OffsetDateTime atualizadoEm,
        Situacao situacao,
        String erro,
        T dados) {
}
