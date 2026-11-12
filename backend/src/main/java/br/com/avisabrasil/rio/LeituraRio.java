package br.com.avisabrasil.rio;

import java.time.LocalDateTime;

/**
 * Leitura telemétrica da ANA.
 *
 * @param dataHora horário da medição, no fuso da comunidade (America/Manaus)
 * @param cotaCm nível na régua da estação, em centímetros
 * @param vazaoM3s vazão calculada pela ANA (pode ser nula)
 * @param chuvaMm chuva no intervalo (pode ser nula)
 */
public record LeituraRio(LocalDateTime dataHora, Double cotaCm, Double vazaoM3s, Double chuvaMm) {
}
