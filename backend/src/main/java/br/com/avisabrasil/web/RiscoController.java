package br.com.avisabrasil.web;

import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.risco.AvaliacaoDeRisco;
import br.com.avisabrasil.risco.MotorDeRisco;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risco")
@Tag(name = "Risco", description = "Motor de risco: nível da comunidade (cheia ou seca) e alertas gerados")
public class RiscoController {

    private final MotorDeRisco motor;

    public RiscoController(MotorDeRisco motor) {
        this.motor = motor;
    }

    @GetMapping
    @Operation(summary = "Nível de risco atual da comunidade, com todos os fatores e o principal",
            description = "Maior risco entre cheia e seca. Entradas: régua da ANA, sensor (simulado), chuva prevista "
                    + "(Open-Meteo) e vazão prevista (GloFAS). O campo simulado indica se o nível veio de dado simulado.")
    public AvaliacaoDeRisco atual() {
        return motor.atual();
    }

    @GetMapping("/alertas")
    @Operation(summary = "Alertas gerados (só quando o nível sobe, e \"situação normalizada\" quando volta ao Normal)")
    public List<Alerta> alertas(@RequestParam(defaultValue = "50") int quantidade) {
        return motor.ultimosAlertas(Math.min(Math.max(quantidade, 1), 200));
    }
}
