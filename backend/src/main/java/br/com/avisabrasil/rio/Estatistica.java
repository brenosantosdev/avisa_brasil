package br.com.avisabrasil.rio;

import java.util.List;

/** Contas estatísticas simples e explicáveis. */
public final class Estatistica {

    private Estatistica() {
    }

    /** Percentil com interpolação linear numa lista já ordenada. */
    public static double percentil(List<Double> ordenados, double p) {
        double posicao = (p / 100.0) * (ordenados.size() - 1);
        int abaixo = (int) Math.floor(posicao);
        int acima = (int) Math.ceil(posicao);
        double fracao = posicao - abaixo;
        return ordenados.get(abaixo) + (ordenados.get(acima) - ordenados.get(abaixo)) * fracao;
    }

    /**
     * Inclinação da reta que melhor passa pelos pontos (regressão linear, mínimos quadrados).
     * Com x em horas e y em cm, o resultado é a velocidade em cm por hora.
     */
    public static double inclinacao(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) {
            return 0;
        }
        double mediaX = 0;
        double mediaY = 0;
        for (int i = 0; i < n; i++) {
            mediaX += x[i];
            mediaY += y[i];
        }
        mediaX /= n;
        mediaY /= n;
        double cov = 0;
        double var = 0;
        for (int i = 0; i < n; i++) {
            cov += (x[i] - mediaX) * (y[i] - mediaY);
            var += (x[i] - mediaX) * (x[i] - mediaX);
        }
        return var == 0 ? 0 : cov / var;
    }
}
