package br.com.avisabrasil.previsao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converte o JSON do Open-Meteo em {@link PrevisaoTempo}. */
public final class OpenMeteoConversor {

    /** Abaixo disso, o dia não tem "período mais chuvoso". */
    private static final double CHUVA_MINIMA_PERIODO_MM = 0.5;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenMeteoConversor() {
    }

    public static PrevisaoTempo converter(String json) {
        JsonNode raiz = ler(json);
        JsonNode diario = raiz.path("daily");
        JsonNode datas = diario.path("time");
        if (!datas.isArray() || datas.isEmpty()) {
            throw new IllegalArgumentException("Resposta do Open-Meteo sem dados diários");
        }

        Map<LocalDate, EnumMap<Periodo, Double>> chuvaPorDia = somarChuvaPorPeriodo(raiz.path("hourly"));

        List<PrevisaoTempo.Dia> dias = new ArrayList<>();
        for (int i = 0; i < datas.size(); i++) {
            LocalDate data = LocalDate.parse(datas.get(i).asText());
            double chuva = valorOuZero(diario.path("precipitation_sum"), i);
            Double maxima = numero(diario.path("temperature_2m_max"), i);
            Integer codigo = inteiro(diario.path("weather_code"), i);
            EnumMap<Periodo, Double> periodos = chuvaPorDia.getOrDefault(data, periodosZerados());

            dias.add(new PrevisaoTempo.Dia(
                    data,
                    chuva,
                    inteiro(diario.path("precipitation_probability_max"), i),
                    maxima,
                    numero(diario.path("temperature_2m_min"), i),
                    Ceu.doCodigoWmo(codigo),
                    CategoriaChuva.doTotalDiario(chuva),
                    Baldes.porMetroQuadrado(chuva),
                    periodos,
                    periodoMaisChuvoso(periodos),
                    maxima != null && maxima >= PrevisaoTempo.CALOR_FORTE_GRAUS));
        }
        return new PrevisaoTempo(raiz.path("latitude").asDouble(), raiz.path("longitude").asDouble(), dias);
    }

    private static Map<LocalDate, EnumMap<Periodo, Double>> somarChuvaPorPeriodo(JsonNode horario) {
        Map<LocalDate, EnumMap<Periodo, Double>> resultado = new HashMap<>();
        JsonNode horas = horario.path("time");
        JsonNode chuva = horario.path("precipitation");
        for (int i = 0; i < horas.size(); i++) {
            LocalDateTime momento = LocalDateTime.parse(horas.get(i).asText());
            resultado.computeIfAbsent(momento.toLocalDate(), d -> periodosZerados())
                    .merge(Periodo.doHorario(momento.getHour()), valorOuZero(chuva, i), Double::sum);
        }
        resultado.values().forEach(p -> p.replaceAll((periodo, mm) -> Baldes.arredondar(mm, 1)));
        return resultado;
    }

    private static Periodo periodoMaisChuvoso(Map<Periodo, Double> periodos) {
        return periodos.entrySet().stream()
                .filter(e -> e.getValue() >= CHUVA_MINIMA_PERIODO_MM)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static EnumMap<Periodo, Double> periodosZerados() {
        EnumMap<Periodo, Double> mapa = new EnumMap<>(Periodo.class);
        for (Periodo p : Periodo.values()) {
            mapa.put(p, 0.0);
        }
        return mapa;
    }

    static JsonNode ler(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON inválido: " + e.getOriginalMessage(), e);
        }
    }

    static Double numero(JsonNode lista, int i) {
        JsonNode n = lista.get(i);
        return n == null || n.isNull() ? null : n.asDouble();
    }

    private static Integer inteiro(JsonNode lista, int i) {
        JsonNode n = lista.get(i);
        return n == null || n.isNull() ? null : n.asInt();
    }

    private static double valorOuZero(JsonNode lista, int i) {
        Double valor = numero(lista, i);
        return valor == null ? 0.0 : valor;
    }
}
