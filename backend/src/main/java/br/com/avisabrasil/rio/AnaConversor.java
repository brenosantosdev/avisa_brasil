package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.InfraestruturaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Converte as respostas do HidroWebService da ANA. */
public final class AnaConversor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** "2026-11-11 20:45:00.0" */
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]");
    /** Fuso dos horários da telemetria da ANA. */
    static final ZoneId FUSO_ANA = ZoneId.of("America/Sao_Paulo");

    private AnaConversor() {
    }

    /**
     * Leituras telemétricas em ordem de horário; sem registros no período, lista vazia.
     * A ANA informa o horário de Brasília (UTC−3): em 11/11/2026 a leitura "21:45" já estava
     * disponível às 21:10 de Manaus. Aqui o horário é convertido para o fuso da comunidade.
     */
    public static List<LeituraRio> telemetria(String json) {
        List<LeituraRio> leituras = new ArrayList<>();
        for (JsonNode item : itens(json)) {
            Double cota = numero(item.path("Cota_Adotada"));
            if (cota == null) {
                continue;
            }
            LocalDateTime brasilia = LocalDateTime.parse(item.path("Data_Hora_Medicao").asText(), DATA_HORA);
            leituras.add(new LeituraRio(
                    brasilia.atZone(FUSO_ANA).withZoneSameInstant(InfraestruturaConfig.FUSO_COMUNIDADE).toLocalDateTime(),
                    cota,
                    numero(item.path("Vazao_Adotada")),
                    numero(item.path("Chuva_Adotada"))));
        }
        leituras.sort(Comparator.comparing(LeituraRio::dataHora));
        return leituras;
    }

    /**
     * Cota diária (cm) da régua convencional. A ANA manda, para cada mês, linhas com a média diária
     * (Mediadiaria=1) e linhas das leituras das 7h e das 17h, às vezes em dois níveis de
     * consistência (1 = bruto, 2 = consistido). Para cada mês usa o nível mais consistido e, nele,
     * a média diária; se não houver média, faz a média das leituras do dia.
     */
    public static Map<LocalDate, Double> cotasDiarias(String json) {
        Map<YearMonth, List<JsonNode>> porMes = new HashMap<>();
        for (JsonNode item : itens(json)) {
            LocalDateTime dataHora = LocalDateTime.parse(item.path("Data_Hora_Dado").asText(), DATA_HORA);
            porMes.computeIfAbsent(YearMonth.from(dataHora), m -> new ArrayList<>()).add(item);
        }
        Map<LocalDate, Double> resultado = new TreeMap<>();
        porMes.forEach((mes, linhas) -> {
            int consistencia = linhas.stream().mapToInt(l -> l.path("nivelconsistencia").asInt(1)).max().orElse(1);
            List<JsonNode> doNivel = linhas.stream().filter(l -> l.path("nivelconsistencia").asInt(1) == consistencia).toList();
            List<JsonNode> medias = doNivel.stream().filter(l -> l.path("Mediadiaria").asInt(0) == 1).toList();
            List<JsonNode> usar = medias.isEmpty() ? doNivel : medias;
            for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
                String campo = String.format("Cota_%02d", dia);
                double soma = 0;
                int n = 0;
                for (JsonNode linha : usar) {
                    Double cota = numero(linha.path(campo));
                    if (cota != null) {
                        soma += cota;
                        n++;
                    }
                }
                if (n > 0) {
                    resultado.put(mes.atDay(dia), soma / n);
                }
            }
        });
        return resultado;
    }

    /** Itens da resposta; "Não houve retorno de registros" vem com items nulo e conta como vazio. */
    private static JsonNode itens(String json) {
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Resposta da ANA não é JSON", e);
        }
        String status = raiz.path("status").asText("");
        if (!status.startsWith("200") && !"OK".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("ANA respondeu " + status + ": " + raiz.path("message").asText(""));
        }
        JsonNode itens = raiz.path("items");
        return itens.isArray() ? itens : MAPPER.createArrayNode();
    }

    private static Double numero(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) {
            return null;
        }
        String texto = no.asText("").trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
