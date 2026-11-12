package br.com.avisabrasil.previsao;

/**
 * O que é "normal" para o rio numa época do ano, a partir dos anos anteriores.
 * Metade dos dias da amostra ficou entre {@code p25} e {@code p75}.
 *
 * @param p5 só 5% dos dias da amostra ficaram abaixo deste valor
 * @param janelaDias em cada ano, quantos dias antes e depois da mesma data entram na conta
 * @param amostras total de dias usados
 */
public record FaixaNormal(
        double p5,
        double p10,
        double p25,
        double mediana,
        double p75,
        double p90,
        int anoInicial,
        int anoFinal,
        int janelaDias,
        int amostras) {

    /** Compara uma vazão com esta faixa. */
    public ComparacaoNormal comparar(double vazao) {
        if (vazao < p10) {
            return ComparacaoNormal.MUITO_ABAIXO;
        }
        if (vazao < p25) {
            return ComparacaoNormal.ABAIXO;
        }
        if (vazao <= p75) {
            return ComparacaoNormal.NORMAL;
        }
        if (vazao <= p90) {
            return ComparacaoNormal.ACIMA;
        }
        return ComparacaoNormal.MUITO_ACIMA;
    }
}
