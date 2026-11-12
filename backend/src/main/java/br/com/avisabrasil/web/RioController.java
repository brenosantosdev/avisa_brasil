package br.com.avisabrasil.web;

import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.rio.RioAgora;
import br.com.avisabrasil.rio.RioAgoraServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rio")
@Tag(name = "Rio agora", description = "Nível do rio medido pela ANA, cotas de referência e histórico de cheias e secas")
public class RioController {

    private final RioAgoraServico rio;

    public RioController(RioAgoraServico rio) {
        this.rio = rio;
    }

    @GetMapping("/agora")
    @Operation(summary = "Nível atual, tendência, cotas de referência e maiores cheias e secas",
            description = "Telemetria da estação principal (atualizada a cada 15 min). Cotas provisórias quando não há "
                    + "cota oficial. Se ANA e GloFAS discordarem sobre seca ou cheia, vale a ANA (campo divergem).")
    public RespostaComFonte<RioAgora> agora() {
        return rio.consultar();
    }
}
