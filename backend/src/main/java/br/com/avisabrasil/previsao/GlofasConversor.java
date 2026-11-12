package br.com.avisabrasil.previsao;

import static br.com.avisabrasil.previsao.OpenMeteoConversor.ler;
import static br.com.avisabrasil.previsao.OpenMeteoConversor.numero;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converte o JSON da API de cheias (GloFAS) do Open-Meteo. */
public final class GlofasConversor {

    private GlofasConversor() {
    }

    public static SerieGlofas converterPrevisao(String json) {
        JsonNode raiz = ler(json);
        JsonNode diario = raiz.path("daily");
        JsonNode datas = exigirDatas(diario);
        List<SerieGlofas.Dia> dias = new ArrayList<>();
        for (int i = 0; i < datas.size(); i++) {
            dias.add(new SerieGlofas.Dia(
                    LocalDate.parse(datas.get(i).asText()),
                    numero(diario.path("river_discharge"), i),
                    numero(diario.path("river_discharge_median"), i),
                    numero(diario.path("river_discharge_p25"), i),
                    numero(diario.path("river_discharge_p75"), i)));
        }
        if (dias.stream().allMatch(d -> d.valorPrincipal() == null)) {
            throw new IllegalArgumentException("Resposta do GloFAS sem nenhum valor de vazão");
        }
        return new SerieGlofas(raiz.path("latitude").asDouble(), raiz.path("longitude").asDouble(), dias);
    }

    /** Ignora os dias sem valor (antes de 1997 a série vem vazia). */
    public static SerieHistorica converterHistorico(String json) {
        JsonNode diario = ler(json).path("daily");
        JsonNode datas = exigirDatas(diario);
        Map<LocalDate, Double> valores = new HashMap<>();
        for (int i = 0; i < datas.size(); i++) {
            Double vazao = numero(diario.path("river_discharge"), i);
            if (vazao != null) {
                valores.put(LocalDate.parse(datas.get(i).asText()), vazao);
            }
        }
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("Histórico do GloFAS sem nenhum valor de vazão");
        }
        return new SerieHistorica(valores);
    }

    private static JsonNode exigirDatas(JsonNode diario) {
        JsonNode datas = diario.path("time");
        if (!datas.isArray() || datas.isEmpty()) {
            throw new IllegalArgumentException("Resposta do GloFAS sem dados diários");
        }
        return datas;
    }
}
