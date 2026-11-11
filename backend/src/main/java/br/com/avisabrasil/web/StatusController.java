package br.com.avisabrasil.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint simples para confirmar que o sistema está no ar. */
@RestController
@RequestMapping("/api/status")
@Tag(name = "Status", description = "Verificação de funcionamento do sistema")
public class StatusController {

    private static final ZoneId FUSO_MANAUS = ZoneId.of("America/Manaus");

    @Value("${spring.application.name}")
    private String nomeAplicacao;

    @GetMapping
    @Operation(summary = "Informa se o sistema está no ar e o horário local da comunidade")
    public Map<String, Object> status() {
        return Map.of(
                "sistema", nomeAplicacao,
                "situacao", "no ar",
                "horarioLocal", OffsetDateTime.now(FUSO_MANAUS).toString(),
                "fuso", FUSO_MANAUS.getId());
    }
}
