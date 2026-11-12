package br.com.avisabrasil.previsao;

/**
 * Classificação simples e explicável da chuva total de um dia.
 *
 * <pre>
 * menos de 1 mm   sem chuva
 * 1 a 10 mm       fraca
 * 10 a 25 mm      moderada
 * 25 a 50 mm      forte
 * 50 mm ou mais   muito forte
 * </pre>
 */
public enum CategoriaChuva {
    SEM_CHUVA, FRACA, MODERADA, FORTE, MUITO_FORTE;

    public static CategoriaChuva doTotalDiario(double milimetros) {
        if (milimetros < 1) {
            return SEM_CHUVA;
        }
        if (milimetros < 10) {
            return FRACA;
        }
        if (milimetros < 25) {
            return MODERADA;
        }
        if (milimetros < 50) {
            return FORTE;
        }
        return MUITO_FORTE;
    }
}
