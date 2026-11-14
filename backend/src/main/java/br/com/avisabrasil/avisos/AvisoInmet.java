package br.com.avisabrasil.avisos;

import java.time.OffsetDateTime;

/**
 * Aviso meteorológico oficial do INMET que vale para o município da comunidade.
 *
 * @param id número do aviso no INMET
 * @param evento nome do evento como o INMET escreve ("Tempestade", "Chuvas Intensas"...)
 * @param eventoCodigo o mesmo nome sem acentos, para a tela traduzir ("CHUVAS_INTENSAS")
 * @param grau grau de severidade do INMET (amarelo, laranja, vermelho); nulo se o INMET não informar
 * @param descricao texto oficial do INMET, em português
 * @param instrucao orientação oficial do INMET, em português
 * @param link página do aviso no site do INMET
 */
public record AvisoInmet(
        String id,
        String evento,
        String eventoCodigo,
        Grau grau,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String descricao,
        String instrucao,
        String link) {

    /** Graus de severidade dos avisos do INMET (no protocolo CAP: Moderate, Severe, Extreme). */
    public enum Grau {
        /** Amarelo. */
        PERIGO_POTENCIAL,
        /** Laranja. */
        PERIGO,
        /** Vermelho. */
        GRANDE_PERIGO
    }
}
