package br.com.avisabrasil.risco;

import br.com.avisabrasil.rio.Nivel;
import java.util.List;

/**
 * Resultado das regras para uma medição de nível (régua da ANA ou sensor).
 *
 * @param nivelCheia nível só pelas cotas de cheia
 * @param nivelSeca nível de seca: o pior entre as cotas de seca e a comparação com a época
 * @param nivel o pior de todos, incluindo o preventivo
 * @param tipo cheia ou seca (nulo quando Normal)
 * @param proximo próxima cota na direção do risco atual (ou do movimento do rio, quando Normal)
 */
public record AvaliacaoMedicao(
        FonteRisco fonte,
        boolean simulado,
        double cotaCm,
        Double velocidadeCmPorDia,
        Nivel nivelCheia,
        Nivel nivelSeca,
        Nivel nivel,
        TipoRisco tipo,
        List<Fator> fatores,
        Proximo proximo) {

    /**
     * @param faltaCm quanto falta (sempre positivo) até a cota
     * @param horas quanto tempo até lá, se o rio estiver indo nessa direção; nulo se não estiver
     */
    public record Proximo(TipoRisco tipo, Nivel nivel, double cotaCm, double faltaCm, Double horas) {
    }
}
