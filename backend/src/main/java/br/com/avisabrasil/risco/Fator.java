package br.com.avisabrasil.risco;

import br.com.avisabrasil.rio.Nivel;
import java.util.Map;

/**
 * Um motivo para o nível de risco, com os números que explicam a decisão.
 *
 * @param motivo regra que gerou o fator (a tela traduz pelo código)
 * @param dados números usados na regra (cota, limite, horas...), para a explicação na tela
 * @param simulado veio de dado simulado (o sensor desta versão)
 * @param preventivo subiu antes de a cota ser atingida, pela tendência
 */
public record Fator(
        FonteRisco fonte,
        TipoRisco tipo,
        Nivel nivel,
        Motivo motivo,
        Map<String, Object> dados,
        boolean simulado,
        boolean preventivo) {

    public enum Motivo {
        /** A cota passou de uma cota de cheia. */
        COTA_CHEIA,
        /** A cota ficou abaixo de uma cota de seca. */
        COTA_SECA,
        /** A cota está entre as mais baixas já vistas nesta época do ano. */
        SECA_EPOCA,
        /** Pela velocidade de subida, a próxima cota de cheia chega logo. */
        PREVENTIVO_CHEIA,
        /** Pela velocidade de descida, a próxima cota de seca chega logo. */
        PREVENTIVO_SECA,
        /** Chuva forte prevista para hoje ou amanhã. */
        CHUVA_FORTE,
        /** O modelo GloFAS prevê subida rápida do rio. */
        SUBIDA_RAPIDA_PREVISTA
    }
}
