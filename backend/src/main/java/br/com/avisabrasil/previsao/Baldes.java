package br.com.avisabrasil.previsao;

/**
 * Converte quantidades de água em baldes, para explicar os números com algo do dia a dia.
 * 1 mm de chuva = 1 litro por metro quadrado; um balde grande tem uns 20 litros.
 */
public final class Baldes {

    public static final double LITROS_POR_BALDE = 20.0;

    private Baldes() {
    }

    /** Baldes de água que caem em cada metro quadrado, com uma casa decimal. */
    public static double porMetroQuadrado(double milimetrosDeChuva) {
        return arredondar(milimetrosDeChuva / LITROS_POR_BALDE, 1);
    }

    /** Baldes de água que passam por segundo num rio com a vazão dada (1 m³ = 1000 litros). */
    public static long porSegundo(double vazaoM3s) {
        return Math.round(vazaoM3s * 1000 / LITROS_POR_BALDE);
    }

    static double arredondar(double valor, int casas) {
        double fator = Math.pow(10, casas);
        return Math.round(valor * fator) / fator;
    }
}
