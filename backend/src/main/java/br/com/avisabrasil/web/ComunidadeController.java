package br.com.avisabrasil.web;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comunidade")
@Tag(name = "Comunidade", description = "Comunidade monitorada")
public class ComunidadeController {

    private final AvisaBrasilProperties propriedades;

    public ComunidadeController(AvisaBrasilProperties propriedades) {
        this.propriedades = propriedades;
    }

    @GetMapping
    @Operation(summary = "Nome, rio e coordenadas da comunidade piloto")
    public AvisaBrasilProperties.Comunidade comunidade() {
        return propriedades.comunidade();
    }
}
