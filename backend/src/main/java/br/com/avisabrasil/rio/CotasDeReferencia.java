package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import java.util.List;
import java.util.Optional;

/**
 * Cotas de Atenção, Alerta e Emergência (cm, na régua da estação principal).
 *
 * @param origem OFICIAL_CONFIGURADA quando vieram da configuração com fonte; PROVISORIA quando
 *     calculadas do histórico da ANA (não há cota oficial para a estação)
 * @param fonte de onde vieram (texto mostrado na tela)
 * @param anoInicial primeiro ano do histórico usado no cálculo provisório
 * @param anoFinal último ano do histórico usado no cálculo provisório
 */
public record CotasDeReferencia(
        double atencaoCm,
        double alertaCm,
        double emergenciaCm,
        Origem origem,
        String fonte,
        Integer anoInicial,
        Integer anoFinal,
        Integer anos) {

    public enum Origem { OFICIAL_CONFIGURADA, PROVISORIA }

    /** Mínimo de anos completos para calcular cotas provisórias. */
    static final int MINIMO_ANOS = 10;

    /**
     * Cotas configuradas (com fonte) têm prioridade. Sem elas, calcula provisórias a partir das
     * maiores cheias de cada ano:
     * <ul>
     *   <li>Atenção: cheia que acontece em metade dos anos (mediana);</li>
     *   <li>Alerta: cheia que só 1 em cada 5 anos passa (percentil 80);</li>
     *   <li>Emergência: cheia que só 1 em cada 20 anos passa (percentil 95).</li>
     * </ul>
     */
    public static Optional<CotasDeReferencia> definir(AvisaBrasilProperties.Cotas configuradas, ExtremosAnuais extremos) {
        if (configuradas != null && configuradas.atencaoCm() != null && configuradas.alertaCm() != null
                && configuradas.emergenciaCm() != null) {
            if (configuradas.fonte() == null || configuradas.fonte().isBlank()) {
                throw new IllegalStateException("Cotas configuradas sem fonte: preencha avisabrasil.ana.cotas.fonte");
            }
            return Optional.of(new CotasDeReferencia(configuradas.atencaoCm(), configuradas.alertaCm(),
                    configuradas.emergenciaCm(), Origem.OFICIAL_CONFIGURADA, configuradas.fonte(), null, null, null));
        }
        if (extremos == null || extremos.anosCompletos() < MINIMO_ANOS) {
            return Optional.empty();
        }
        List<ExtremosAnuais.Evento> cheias = extremos.cheiasPorAno();
        List<Double> ordenadas = cheias.stream().map(ExtremosAnuais.Evento::cotaCm).sorted().toList();
        return Optional.of(new CotasDeReferencia(
                Math.round(Estatistica.percentil(ordenadas, 50)),
                Math.round(Estatistica.percentil(ordenadas, 80)),
                Math.round(Estatistica.percentil(ordenadas, 95)),
                Origem.PROVISORIA,
                "Cálculo do AvisaBrasil a partir das maiores cheias anuais registradas pela ANA nesta estação",
                cheias.get(0).ano(),
                cheias.get(cheias.size() - 1).ano(),
                cheias.size()));
    }

    /** Nível pela cota: NORMAL, ATENCAO, ALERTA ou EMERGENCIA. */
    public Nivel nivel(double cotaCm) {
        if (cotaCm >= emergenciaCm) {
            return Nivel.EMERGENCIA;
        }
        if (cotaCm >= alertaCm) {
            return Nivel.ALERTA;
        }
        if (cotaCm >= atencaoCm) {
            return Nivel.ATENCAO;
        }
        return Nivel.NORMAL;
    }

    /** Próxima cota acima do nível atual, ou vazio se já está em Emergência. */
    public Optional<Nivel> proximoNivel(double cotaCm) {
        return switch (nivel(cotaCm)) {
            case NORMAL -> Optional.of(Nivel.ATENCAO);
            case ATENCAO -> Optional.of(Nivel.ALERTA);
            case ALERTA -> Optional.of(Nivel.EMERGENCIA);
            case EMERGENCIA -> Optional.empty();
        };
    }

    public double cota(Nivel nivel) {
        return switch (nivel) {
            case NORMAL -> 0;
            case ATENCAO -> atencaoCm;
            case ALERTA -> alertaCm;
            case EMERGENCIA -> emergenciaCm;
        };
    }
}
