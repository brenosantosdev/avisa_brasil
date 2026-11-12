package br.com.avisabrasil.risco;

import br.com.avisabrasil.previsao.FaixaNormal;
import br.com.avisabrasil.rio.CotasDeReferencia;
import br.com.avisabrasil.rio.Nivel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras simples e explicáveis do motor de risco para uma medição de nível.
 *
 * <ol>
 *   <li>Cheia: cota acima das cotas de cheia (Atenção, Alerta, Emergência).</li>
 *   <li>Seca pelas cotas: cota abaixo das cotas de seca.</li>
 *   <li>Seca pela época: cota entre as mais baixas para esta época do ano nos anos anteriores
 *       (abaixo dos 25% mais baixos = Atenção; 10% = Alerta; 5% = Emergência).</li>
 *   <li>Preventivo: se a tendência indica que a próxima cota chega em poucas horas, já sobe o nível.</li>
 * </ol>
 * O nível final é o maior de todos (cheia ou seca).
 */
public final class RegrasDeRisco {

    /** Abaixo de 1 cm por dia o rio é considerado parado (não gera previsão de tempo). */
    static final double PARADO_CM_DIA = 1.0;

    private RegrasDeRisco() {
    }

    /**
     * @param normal faixa normal da época (pode ser nula se ainda não há histórico)
     * @param velocidadeCmPorDia positiva subindo, negativa descendo (pode ser nula)
     */
    public static AvaliacaoMedicao avaliarMedicao(FonteRisco fonte, boolean simulado, double cotaCm,
            Double velocidadeCmPorDia, FaixaNormal normal, CotasDeReferencia cotas,
            int horasAntecedenciaCheia, int horasAntecedenciaSeca) {
        List<Fator> fatores = new ArrayList<>();

        Nivel nivelCheia = cotas.nivelCheia(cotaCm);
        if (nivelCheia != Nivel.NORMAL) {
            fatores.add(fator(fonte, TipoRisco.CHEIA, nivelCheia, Fator.Motivo.COTA_CHEIA, simulado, false,
                    "cotaCm", cotaCm, "limiteCm", cotas.cheia().cota(nivelCheia)));
        }
        Nivel secaPelasCotas = cotas.nivelSeca(cotaCm);
        if (secaPelasCotas != Nivel.NORMAL) {
            fatores.add(fator(fonte, TipoRisco.SECA, secaPelasCotas, Fator.Motivo.COTA_SECA, simulado, false,
                    "cotaCm", cotaCm, "limiteCm", cotas.seca().cota(secaPelasCotas)));
        }
        Nivel secaPelaEpoca = Nivel.NORMAL;
        if (normal != null) {
            secaPelaEpoca = nivelSecaPelaEpoca(cotaCm, normal);
            if (secaPelaEpoca != Nivel.NORMAL) {
                double limite = secaPelaEpoca == Nivel.EMERGENCIA ? normal.p5()
                        : secaPelaEpoca == Nivel.ALERTA ? normal.p10() : normal.p25();
                int percentual = secaPelaEpoca == Nivel.EMERGENCIA ? 5 : secaPelaEpoca == Nivel.ALERTA ? 10 : 25;
                fatores.add(fator(fonte, TipoRisco.SECA, secaPelaEpoca, Fator.Motivo.SECA_EPOCA, simulado, false,
                        "cotaCm", cotaCm, "limiteCm", Math.round(limite), "percentual", percentual,
                        "anoInicial", normal.anoInicial(), "anoFinal", normal.anoFinal()));
            }
        }
        Nivel nivelSeca = max(secaPelasCotas, secaPelaEpoca);

        // preventivo: a próxima cota (nas cotas fixas) chega dentro do prazo, pela tendência
        if (velocidadeCmPorDia != null && velocidadeCmPorDia >= PARADO_CM_DIA) {
            nivelCheia.proximo().ifPresent(prox -> {
                double falta = cotas.cheia().cota(prox) - cotaCm;
                double horas = falta / (velocidadeCmPorDia / 24);
                if (horas <= horasAntecedenciaCheia) {
                    fatores.add(fator(fonte, TipoRisco.CHEIA, prox, Fator.Motivo.PREVENTIVO_CHEIA, simulado, true,
                            "cotaCm", cotaCm, "limiteCm", cotas.cheia().cota(prox), "horas", arredondar(horas),
                            "velocidadeCmPorDia", velocidadeCmPorDia));
                }
            });
        }
        if (velocidadeCmPorDia != null && velocidadeCmPorDia <= -PARADO_CM_DIA) {
            secaPelasCotas.proximo().ifPresent(prox -> {
                double falta = cotaCm - cotas.seca().cota(prox);
                double horas = falta / (-velocidadeCmPorDia / 24);
                if (horas <= horasAntecedenciaSeca) {
                    fatores.add(fator(fonte, TipoRisco.SECA, prox, Fator.Motivo.PREVENTIVO_SECA, simulado, true,
                            "cotaCm", cotaCm, "limiteCm", cotas.seca().cota(prox), "horas", arredondar(horas),
                            "velocidadeCmPorDia", velocidadeCmPorDia));
                }
            });
        }

        Optional<Fator> principal = principal(fatores);
        Nivel nivel = principal.map(Fator::nivel).orElse(Nivel.NORMAL);
        TipoRisco tipo = principal.map(Fator::tipo).orElse(null);
        return new AvaliacaoMedicao(fonte, simulado, cotaCm, velocidadeCmPorDia, nivelCheia, nivelSeca, nivel, tipo,
                List.copyOf(fatores), proximo(cotaCm, velocidadeCmPorDia, tipo, nivel, cotas));
    }

    /** Seca pela época: abaixo dos 25% (Atenção), 10% (Alerta) ou 5% (Emergência) mais baixos. */
    public static Nivel nivelSecaPelaEpoca(double cotaCm, FaixaNormal normal) {
        if (cotaCm < normal.p5()) {
            return Nivel.EMERGENCIA;
        }
        if (cotaCm < normal.p10()) {
            return Nivel.ALERTA;
        }
        return cotaCm < normal.p25() ? Nivel.ATENCAO : Nivel.NORMAL;
    }

    /**
     * O fator mais grave. Em empate: medido antes de preventivo, e a fonte mais confiável primeiro
     * (ANA, sensor, chuva, modelo).
     */
    public static Optional<Fator> principal(List<Fator> fatores) {
        return fatores.stream().min(Comparator.comparing((Fator f) -> f.nivel()).reversed()
                .thenComparing(Fator::preventivo)
                .thenComparing(Fator::fonte));
    }

    /** Próxima cota na direção do risco atual; quando Normal, na direção em que o rio está indo. */
    private static AvaliacaoMedicao.Proximo proximo(double cotaCm, Double velocidade, TipoRisco tipo, Nivel nivel,
            CotasDeReferencia cotas) {
        TipoRisco direcao = tipo != null ? tipo
                : (velocidade != null && velocidade <= -PARADO_CM_DIA ? TipoRisco.SECA : TipoRisco.CHEIA);
        Nivel atual = tipo == direcao ? nivel : Nivel.NORMAL;
        Optional<Nivel> prox = atual.proximo();
        if (prox.isEmpty()) {
            return null;
        }
        boolean cheia = direcao == TipoRisco.CHEIA;
        double cota = cheia ? cotas.cheia().cota(prox.get()) : cotas.seca().cota(prox.get());
        double falta = cheia ? cota - cotaCm : cotaCm - cota;
        if (falta <= 0) {
            return null;
        }
        Double horas = null;
        if (velocidade != null && Math.abs(velocidade) >= PARADO_CM_DIA && (cheia == velocidade > 0)) {
            horas = arredondar(falta / (Math.abs(velocidade) / 24));
        }
        return new AvaliacaoMedicao.Proximo(direcao, prox.get(), cota, Math.round(falta), horas);
    }

    static Fator fator(FonteRisco fonte, TipoRisco tipo, Nivel nivel, Fator.Motivo motivo, boolean simulado,
            boolean preventivo, Object... chavesEValores) {
        Map<String, Object> dados = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            dados.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return new Fator(fonte, tipo, nivel, motivo, dados, simulado, preventivo);
    }

    static Nivel max(Nivel a, Nivel b) {
        return a.maiorQue(b) ? a : b;
    }

    private static double arredondar(double horas) {
        return Math.round(horas * 10) / 10.0;
    }
}
