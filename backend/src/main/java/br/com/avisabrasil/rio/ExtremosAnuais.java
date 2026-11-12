package br.com.avisabrasil.rio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Maior cheia e maior seca de cada ano numa série diária de cotas.
 * Só entram anos com dados suficientes, para um ano com falhas não parecer "seco" ou "cheio".
 */
public final class ExtremosAnuais {

    /** Um ano precisa ter pelo menos isso de dias com leitura para entrar na conta. */
    static final int MINIMO_DIAS_NO_ANO = 300;

    /** @param cotaCm cota na régua da estação, em centímetros */
    public record Evento(int ano, LocalDate data, double cotaCm) {
    }

    private final List<Evento> cheias = new ArrayList<>();
    private final List<Evento> secas = new ArrayList<>();

    public ExtremosAnuais(NavigableMap<LocalDate, Double> serie) {
        Map<Integer, TreeMap<LocalDate, Double>> porAno = new TreeMap<>();
        serie.forEach((data, cota) -> porAno.computeIfAbsent(data.getYear(), a -> new TreeMap<>()).put(data, cota));
        porAno.forEach((ano, dias) -> {
            if (dias.size() < MINIMO_DIAS_NO_ANO) {
                return;
            }
            var max = dias.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            var min = dias.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow();
            cheias.add(new Evento(ano, max.getKey(), max.getValue()));
            secas.add(new Evento(ano, min.getKey(), min.getValue()));
        });
    }

    /** As {@code n} maiores cheias (uma por ano), da maior para a menor. */
    public List<Evento> maioresCheias(int n) {
        return cheias.stream().sorted(Comparator.comparingDouble(Evento::cotaCm).reversed()).limit(n).toList();
    }

    /** As {@code n} maiores secas (uma por ano), da mais baixa para a menos baixa. */
    public List<Evento> maioresSecas(int n) {
        return secas.stream().sorted(Comparator.comparingDouble(Evento::cotaCm)).limit(n).toList();
    }

    /** Cotas máximas de cada ano, na ordem dos anos. */
    public List<Evento> cheiasPorAno() {
        return List.copyOf(cheias);
    }

    public List<Evento> secasPorAno() {
        return List.copyOf(secas);
    }

    public int anosCompletos() {
        return cheias.size();
    }
}
