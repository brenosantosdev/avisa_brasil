package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import java.util.List;
import java.util.Optional;

/**
 * Cotas de referência de cheia e de seca (cm, na régua da estação principal).
 * Cada faixa é oficial (configurada com fonte) ou provisória (calculada do histórico da ANA).
 */
public record CotasDeReferencia(Faixa cheia, Faixa seca) {

    public enum Origem { OFICIAL_CONFIGURADA, PROVISORIA }

    /** Mínimo de anos completos para calcular cotas provisórias. */
    static final int MINIMO_ANOS = 10;

    /**
     * Uma faixa de três cotas. Na cheia, quanto mais alto, pior (atenção &lt; alerta &lt; emergência);
     * na seca, quanto mais baixo, pior (atenção &gt; alerta &gt; emergência).
     *
     * @param fonte de onde vieram (texto mostrado na tela)
     * @param anoInicial primeiro ano do histórico usado no cálculo provisório
     */
    public record Faixa(
            double atencaoCm,
            double alertaCm,
            double emergenciaCm,
            Origem origem,
            String fonte,
            Integer anoInicial,
            Integer anoFinal,
            Integer anos) {

        /** Cota que marca o começo do nível (NORMAL não tem cota). */
        public double cota(Nivel nivel) {
            return switch (nivel) {
                case NORMAL -> Double.NaN;
                case ATENCAO -> atencaoCm;
                case ALERTA -> alertaCm;
                case EMERGENCIA -> emergenciaCm;
            };
        }
    }

    /**
     * Regras provisórias (escolhidas com o autor em 11/2026), descritas pela frequência:
     * <ul>
     *   <li>Cheia, pelas maiores cheias de cada ano: Atenção = passa em 1 de cada 2 anos (p50);
     *       Alerta = 1 de cada 3 anos (p67); Emergência = 1 de cada 10 anos (p90).</li>
     *   <li>Seca, pelas menores cotas de cada ano: Atenção = fica abaixo em 1 de cada 2 anos (p50);
     *       Alerta = 1 de cada 3 anos (p33); Emergência = 1 de cada 10 anos (p10).</li>
     * </ul>
     * Cotas configuradas têm prioridade e exigem fonte.
     */
    public static Optional<CotasDeReferencia> definir(AvisaBrasilProperties.Cotas config, ExtremosAnuais extremos) {
        Optional<Faixa> cheia = configurada(config == null ? null : config.atencaoCm(),
                config == null ? null : config.alertaCm(), config == null ? null : config.emergenciaCm(),
                config == null ? null : config.fonte(), "avisabrasil.ana.cotas.fonte")
                .or(() -> provisoria(extremos, true));
        Optional<Faixa> seca = configurada(config == null ? null : config.secaAtencaoCm(),
                config == null ? null : config.secaAlertaCm(), config == null ? null : config.secaEmergenciaCm(),
                config == null ? null : config.secaFonte(), "avisabrasil.ana.cotas.seca-fonte")
                .or(() -> provisoria(extremos, false));
        if (cheia.isEmpty() || seca.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CotasDeReferencia(cheia.get(), seca.get()));
    }

    private static Optional<Faixa> configurada(Integer atencao, Integer alerta, Integer emergencia, String fonte,
            String propriedadeFonte) {
        if (atencao == null || alerta == null || emergencia == null) {
            return Optional.empty();
        }
        if (fonte == null || fonte.isBlank()) {
            throw new IllegalStateException("Cotas configuradas sem fonte: preencha " + propriedadeFonte);
        }
        return Optional.of(new Faixa(atencao, alerta, emergencia, Origem.OFICIAL_CONFIGURADA, fonte, null, null, null));
    }

    private static Optional<Faixa> provisoria(ExtremosAnuais extremos, boolean cheia) {
        if (extremos == null || extremos.anosCompletos() < MINIMO_ANOS) {
            return Optional.empty();
        }
        List<ExtremosAnuais.Evento> eventos = cheia ? extremos.cheiasPorAno() : extremos.secasPorAno();
        List<Double> ordenadas = eventos.stream().map(ExtremosAnuais.Evento::cotaCm).sorted().toList();
        double[] p = cheia ? new double[] {50, 67, 90} : new double[] {50, 33, 10};
        return Optional.of(new Faixa(
                Math.round(Estatistica.percentil(ordenadas, p[0])),
                Math.round(Estatistica.percentil(ordenadas, p[1])),
                Math.round(Estatistica.percentil(ordenadas, p[2])),
                Origem.PROVISORIA,
                cheia ? "Cálculo do AvisaBrasil pelas maiores cheias anuais registradas pela ANA nesta estação"
                        : "Cálculo do AvisaBrasil pelas menores cotas anuais registradas pela ANA nesta estação",
                eventos.get(0).ano(),
                eventos.get(eventos.size() - 1).ano(),
                eventos.size()));
    }

    /** Nível de cheia: quanto mais alta a cota, pior. */
    public Nivel nivelCheia(double cotaCm) {
        if (cotaCm >= cheia.emergenciaCm()) {
            return Nivel.EMERGENCIA;
        }
        if (cotaCm >= cheia.alertaCm()) {
            return Nivel.ALERTA;
        }
        return cotaCm >= cheia.atencaoCm() ? Nivel.ATENCAO : Nivel.NORMAL;
    }

    /** Nível de seca: quanto mais baixa a cota, pior. */
    public Nivel nivelSeca(double cotaCm) {
        if (cotaCm <= seca.emergenciaCm()) {
            return Nivel.EMERGENCIA;
        }
        if (cotaCm <= seca.alertaCm()) {
            return Nivel.ALERTA;
        }
        return cotaCm <= seca.atencaoCm() ? Nivel.ATENCAO : Nivel.NORMAL;
    }
}
