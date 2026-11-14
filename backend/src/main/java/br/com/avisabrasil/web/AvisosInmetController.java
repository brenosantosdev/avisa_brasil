package br.com.avisabrasil.web;

import br.com.avisabrasil.avisos.AvisosInmet;
import br.com.avisabrasil.avisos.AvisosInmetServico;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Avisos meteorológicos oficiais do INMET que valem para o município da comunidade. */
@RestController
@RequestMapping("/api/avisos-inmet")
@Tag(name = "Avisos do INMET", description = "Avisos meteorológicos oficiais (RSS + CAP do INMET)")
public class AvisosInmetController {

    private final AvisosInmetServico servico;

    public AvisosInmetController(AvisosInmetServico servico) {
        this.servico = servico;
    }

    @GetMapping
    @Operation(summary = "Avisos ativos e dos últimos dias para o município (filtrado pelo código do IBGE no aviso CAP)")
    public RespostaComFonte<AvisosInmet> avisos() {
        return servico.consultar();
    }
}
