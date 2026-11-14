package br.com.avisabrasil.avisos;

import java.util.List;

/**
 * Avisos do INMET para a comunidade: os que valem agora (ou começam depois) e os que terminaram há poucos
 * dias, para mostrar que o sistema acompanha os avisos mesmo quando não há nenhum ativo.
 */
public record AvisosInmet(
        String municipio,
        String codigoIbge,
        List<AvisoInmet> ativos,
        List<AvisoInmet> recentes,
        int diasRecentes) {
}
