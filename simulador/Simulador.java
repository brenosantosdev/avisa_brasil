import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulador do sensor de nível do AvisaBrasil (não há hardware físico).
 *
 * <p>Envia leituras SIMULADAS para o servidor, começando perto das cotas reais calculadas com a
 * régua da ANA. Usa tempo acelerado: cada passo vale 1 hora simulada (configurável), para o motor
 * de risco calcular a velocidade de subida/descida de forma realista.
 *
 * <pre>
 * Uso (na pasta do projeto, com o servidor rodando):
 *   java simulador/Simulador.java cheia
 *   java simulador/Simulador.java seca
 *   java simulador/Simulador.java normal
 *
 * Se o servidor usar SENSOR_API_KEY, defina a mesma variável no terminal do simulador.
 *
 * Opções:
 *   --url http://localhost:8080   endereço do servidor
 *   --sensor SGC-01               código do sensor
 *   --passos 40                   quantas leituras enviar
 *   --intervalo 2                 segundos reais entre leituras
 *   --horas-por-passo 1           horas simuladas por leitura
 *   --inicio 1300                 nível inicial em cm (padrão: perto das cotas)
 *   --passo 6                     variação por leitura em cm (padrão: cheia +6, seca -6)
 *
 * Modos:
 *   cheia   começa 60 cm abaixo da cota de Atenção de cheia e sobe 6 cm por hora simulada
 *   seca    começa no normal da época e desce 6 cm por hora simulada
 *   normal  fica em volta do normal da época (devolve o sensor ao normal depois de uma simulação)
 * </pre>
 */
public class Simulador {

    private static final ZoneId MANAUS = ZoneId.of("America/Manaus");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !args[0].matches("cheia|seca|normal")) {
            System.out.println("Uso: java simulador/Simulador.java <cheia|seca|normal> [--url ...] [--passos N] [--intervalo s]");
            System.exit(1);
        }
        String modo = args[0];
        Map<String, String> op = opcoes(args);
        String url = op.getOrDefault("url", "http://localhost:8080");
        String sensor = op.getOrDefault("sensor", "SGC-01");
        int passos = Integer.parseInt(op.getOrDefault("passos", "40"));
        double intervalo = Double.parseDouble(op.getOrDefault("intervalo", "2"));
        double horasPorPasso = Double.parseDouble(op.getOrDefault("horas-por-passo", "1"));

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String referencias = get(http, url + "/api/sensores/" + sensor + "/simulacao");
        double cheiaAtencao = numero(referencias, "cheiaAtencaoCm");
        double cheiaEmergencia = numero(referencias, "cheiaEmergenciaCm");
        double secaAtencao = numero(referencias, "secaAtencaoCm");
        double secaEmergencia = numero(referencias, "secaEmergenciaCm");
        double normal = numero(referencias, "normalCm");

        double nivel;
        double passo;
        switch (modo) {
            case "cheia" -> {
                nivel = op.containsKey("inicio") ? Double.parseDouble(op.get("inicio")) : cheiaAtencao - 60;
                passo = op.containsKey("passo") ? Double.parseDouble(op.get("passo")) : 6;
            }
            case "seca" -> {
                // começa no normal da época e desce: passa pela seca "para a época" (Atenção, Alerta,
                // Emergência) e, com mais passos, pelas cotas de seca
                nivel = op.containsKey("inicio") ? Double.parseDouble(op.get("inicio")) : normal;
                passo = op.containsKey("passo") ? Double.parseDouble(op.get("passo")) : -6;
            }
            default -> {
                nivel = op.containsKey("inicio") ? Double.parseDouble(op.get("inicio")) : normal;
                passo = 0;
            }
        }

        System.out.println("=== SIMULAÇÃO (dados de exemplo, não é medição real) ===");
        System.out.printf(Locale.ROOT, "Modo %s | sensor %s | cotas de cheia: Atenção %.2f m, Emergência %.2f m"
                + " | seca: Atenção %.2f m, Emergência %.2f m%n", modo, sensor, cheiaAtencao / 100, cheiaEmergencia / 100,
                secaAtencao / 100, secaEmergencia / 100);
        System.out.printf(Locale.ROOT, "%d leituras, uma a cada %.0f s, cada uma vale %.1f h simulada%n%n",
                passos, intervalo, horasPorPasso);

        Random acaso = new Random(42);
        OffsetDateTime inicio = OffsetDateTime.now(MANAUS).withSecond(0).withNano(0);
        for (int i = 0; i < passos; i++) {
            OffsetDateTime horaSimulada = inicio.plusMinutes(Math.round(i * horasPorPasso * 60));
            if (modo.equals("normal")) {
                // pequenas oscilações em volta do normal da época
                nivel += (normal - nivel) * 0.3 + (acaso.nextDouble() - 0.5) * 2;
            } else if (i > 0) {
                nivel += passo;
            }
            nivel = Math.max(0, Math.round(nivel * 10) / 10.0);
            String corpo = String.format(Locale.ROOT, "{\"nivelCm\": %.1f, \"dataHora\": \"%s\"}", nivel, horaSimulada);
            int status = post(http, url + "/api/sensores/" + sensor + "/leituras", corpo);
            String risco = nivelDeRisco(http, url);
            System.out.printf(Locale.ROOT, "passo %2d/%d | hora simulada %s | nível %6.2f m | HTTP %d | risco: %s%n",
                    i + 1, passos, horaSimulada.format(HORA), nivel / 100, status, risco);
            Thread.sleep(Math.round(intervalo * 1000));
        }
        System.out.println("\nFim da simulação. O sensor deixa de contar 30 min após a última leitura.");
    }

    private static String nivelDeRisco(HttpClient http, String url) {
        try {
            String r = get(http, url + "/api/risco");
            String nivel = texto(r, "nivel");
            String tipo = texto(r, "tipo");
            return nivel + (tipo == null ? "" : " (" + tipo + ")");
        } catch (Exception e) {
            return "?";
        }
    }

    private static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("GET " + url + " respondeu HTTP " + r.statusCode() + ": " + r.body());
        }
        return r.body();
    }

    private static int post(HttpClient http, String url, String json) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        // mesma chave do servidor (variável SENSOR_API_KEY), se estiver definida
        String chave = System.getenv("SENSOR_API_KEY");
        if (chave != null && !chave.isBlank()) {
            pedido.header("X-API-Key", chave.trim());
        }
        int status = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        if (status == 401) {
            throw new IllegalStateException("Servidor recusou a chave do sensor: defina SENSOR_API_KEY igual à do servidor");
        }
        return status;
    }

    /** Lê um número de um JSON simples ("chave": 123.4), sem bibliotecas. */
    static double numero(String json, String chave) {
        Matcher m = Pattern.compile("\"" + chave + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Campo " + chave + " não encontrado em " + json);
        }
        return Double.parseDouble(m.group(1));
    }

    /** Primeiro valor de texto da chave no JSON ("chave": "VALOR"), ou nulo. */
    static String texto(String json, String chave) {
        Matcher m = Pattern.compile("\"" + chave + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Map<String, String> opcoes(String[] args) {
        Map<String, String> op = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            op.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return op;
    }
}
