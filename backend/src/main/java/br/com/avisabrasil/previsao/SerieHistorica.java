package br.com.avisabrasil.previsao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Vazão diária dos anos anteriores, usada para dizer o que é normal em cada época do ano. */
public class SerieHistorica {

    /** Considera 7 dias antes e 7 depois da mesma data em cada ano. */
    static final int JANELA_DIAS = 7;
    /** Com menos amostras que isso, não dá para falar em "normal". */
    static final int MINIMO_AMOSTRAS = 30;

    private final NavigableMap<LocalDate, Double> valores;

    public SerieHistorica(Map<LocalDate, Double> valores) {
        this.valores = Collections.unmodifiableNavigableMap(new TreeMap<>(valores));
    }

    /** Faixa normal para a data, usando só os anos anteriores ao da data. */
    public Optional<FaixaNormal> normalPara(LocalDate data) {
        if (valores.isEmpty()) {
            return Optional.empty();
        }
        int primeiroAno = valores.firstKey().getYear();
        List<Double> amostras = new ArrayList<>();
        int anoInicial = 0;
        int anoFinal = 0;
        for (int ano = primeiroAno; ano < data.getYear(); ano++) {
            LocalDate centro = mesmaDataNoAno(data, ano);
            var janela = valores.subMap(centro.minusDays(JANELA_DIAS), true, centro.plusDays(JANELA_DIAS), true);
            if (!janela.isEmpty()) {
                amostras.addAll(janela.values());
                anoInicial = anoInicial == 0 ? ano : anoInicial;
                anoFinal = ano;
            }
        }
        if (amostras.size() < MINIMO_AMOSTRAS) {
            return Optional.empty();
        }
        Collections.sort(amostras);
        return Optional.of(new FaixaNormal(
                percentil(amostras, 10),
                percentil(amostras, 25),
                percentil(amostras, 50),
                percentil(amostras, 75),
                percentil(amostras, 90),
                anoInicial,
                anoFinal,
                JANELA_DIAS,
                amostras.size()));
    }

    public Optional<Map.Entry<LocalDate, Double>> maximo() {
        return valores.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<LocalDate, Double>> minimo() {
        return valores.entrySet().stream().min(Map.Entry.comparingByValue());
    }

    public Optional<LocalDate> inicio() {
        return valores.isEmpty() ? Optional.empty() : Optional.of(valores.firstKey());
    }

    private static LocalDate mesmaDataNoAno(LocalDate data, int ano) {
        int ultimoDiaDoMes = LocalDate.of(ano, data.getMonth(), 1).lengthOfMonth();
        return LocalDate.of(ano, data.getMonth(), Math.min(data.getDayOfMonth(), ultimoDiaDoMes));
    }

    /** Percentil com interpolação linear numa lista já ordenada. */
    static double percentil(List<Double> ordenados, double p) {
        double posicao = (p / 100.0) * (ordenados.size() - 1);
        int abaixo = (int) Math.floor(posicao);
        int acima = (int) Math.ceil(posicao);
        double fracao = posicao - abaixo;
        return ordenados.get(abaixo) + (ordenados.get(acima) - ordenados.get(abaixo)) * fracao;
    }
}
