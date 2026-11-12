package br.com.avisabrasil.web;

import br.com.avisabrasil.dominio.Leitura;
import br.com.avisabrasil.dominio.Sensor;
import br.com.avisabrasil.risco.SensorServico;
import br.com.avisabrasil.rio.RioAgora;
import br.com.avisabrasil.rio.RioAgoraServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Sensores de nível. Usado pelo simulador Java e pelo firmware ESP32 (Wokwi). */
@RestController
@RequestMapping("/api/sensores")
@Tag(name = "Sensores", description = "Sensor de nível da comunidade (nesta versão, SIMULADO)")
public class SensorController {

    /**
     * @param nivelCm nível na régua da estação principal, em cm
     * @param dataHora horário da medição; vazio = agora (o simulador manda o tempo simulado)
     */
    public record NovaLeitura(@NotNull @DecimalMin("0") @DecimalMax("5000") Double nivelCm, OffsetDateTime dataHora) {
    }

    public record LeituraDto(OffsetDateTime dataHora, double nivelCm, OffsetDateTime recebidoEm) {
    }

    public record SensorDto(String codigo, String nome, boolean simulado, double latitude, double longitude,
            String descricao) {
    }

    /** Cabeçalho com a chave do sensor (variável SENSOR_API_KEY). */
    public static final String CABECALHO_CHAVE = "X-API-Key";
    private static final Logger log = LoggerFactory.getLogger(SensorController.class);

    private final SensorServico sensores;
    private final RioAgoraServico rioAgora;
    private final Clock relogio;
    private final byte[] chave;

    public SensorController(SensorServico sensores, RioAgoraServico rioAgora, Clock relogio,
            @Value("${avisabrasil.segredos.sensor-api-key:}") String chave) {
        this.sensores = sensores;
        this.rioAgora = rioAgora;
        this.relogio = relogio;
        this.chave = chave.trim().getBytes(StandardCharsets.UTF_8);
        if (this.chave.length == 0) {
            log.warn("Sensor: SENSOR_API_KEY não definida; qualquer pessoa pode enviar leituras (use só em demonstração)");
        }
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Dados do sensor")
    public SensorDto sensor(@PathVariable String codigo) {
        Sensor s = buscar(codigo);
        return new SensorDto(s.getCodigo(), s.getNome(), s.isSimulado(), s.getLatitude(), s.getLongitude(), s.getDescricao());
    }

    @GetMapping("/{codigo}/leituras")
    @Operation(summary = "Últimas 48 leituras do sensor (a mais recente primeiro)")
    public List<LeituraDto> leituras(@PathVariable String codigo) {
        return sensores.ultimas(buscar(codigo), 48).stream().map(this::dto).toList();
    }

    @PostMapping("/{codigo}/leituras")
    @Operation(summary = "Enviar leitura de nível (simulador ou ESP32)",
            description = "O motor de risco reavalia na hora. Exemplo: {\"nivelCm\": 1400}. Se SENSOR_API_KEY "
                    + "estiver definida, envie a chave no cabeçalho X-API-Key.")
    public ResponseEntity<LeituraDto> registrar(@PathVariable String codigo, @Valid @RequestBody NovaLeitura nova,
            @RequestHeader(name = CABECALHO_CHAVE, required = false) String chaveRecebida) {
        if (!chaveValida(chaveRecebida)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chave do sensor ausente ou inválida (cabeçalho X-API-Key)");
        }
        Leitura l = sensores.registrar(buscar(codigo), nova.nivelCm(), nova.dataHora());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(l));
    }

    /**
     * Referências para o simulador começar perto das cotas reais: nível atual da ANA, mediana da
     * época e cotas de cheia e seca (todas em cm, na régua da estação principal).
     */
    @GetMapping("/{codigo}/simulacao")
    @Operation(summary = "Referências para o simulador (nível atual, normal da época e cotas)")
    public Map<String, Object> referenciasParaSimulacao(@PathVariable String codigo) {
        buscar(codigo);
        RioAgora rio = rioAgora.consultar().dados();
        if (rio == null || rio.cotas() == null || rio.atual() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ainda sem dados da ANA para calcular as cotas; tente de novo em alguns minutos");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("atualCm", rio.atual().cotaCm());
        r.put("normalCm", rio.normalHoje() == null ? rio.atual().cotaCm() : Math.round(rio.normalHoje().mediana()));
        r.put("cheiaAtencaoCm", rio.cotas().cheia().atencaoCm());
        r.put("cheiaAlertaCm", rio.cotas().cheia().alertaCm());
        r.put("cheiaEmergenciaCm", rio.cotas().cheia().emergenciaCm());
        r.put("secaAtencaoCm", rio.cotas().seca().atencaoCm());
        r.put("secaAlertaCm", rio.cotas().seca().alertaCm());
        r.put("secaEmergenciaCm", rio.cotas().seca().emergenciaCm());
        return r;
    }

    /** Sem chave configurada, aceita (modo demonstração). Com chave, compara em tempo constante. */
    private boolean chaveValida(String recebida) {
        if (chave.length == 0) {
            return true;
        }
        return recebida != null && MessageDigest.isEqual(chave, recebida.trim().getBytes(StandardCharsets.UTF_8));
    }

    private Sensor buscar(String codigo) {
        return sensores.porCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor não encontrado"));
    }

    private LeituraDto dto(Leitura l) {
        var zona = relogio.getZone();
        return new LeituraDto(l.getDataHora().atZone(zona).toOffsetDateTime(), l.getNivelCm(),
                l.getRecebidoEm().atZone(zona).toOffsetDateTime());
    }
}
