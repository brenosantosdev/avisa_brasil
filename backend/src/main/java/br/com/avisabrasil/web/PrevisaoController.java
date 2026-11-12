package br.com.avisabrasil.web;

import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.previsao.PrevisaoRio;
import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.PrevisaoTempo;
import br.com.avisabrasil.previsao.PrevisaoTempoServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Previsões com dados reais. As respostas vêm do cache: se a fonte cair, devolvem o último
 * dado válido com situação DESATUALIZADO.
 */
@RestController
@RequestMapping("/api/previsao")
@Tag(name = "Previsão", description = "Chuva (Open-Meteo) e vazão do rio (GloFAS) para os próximos dias")
public class PrevisaoController {

    private final PrevisaoTempoServico tempo;
    private final PrevisaoRioServico rio;

    public PrevisaoController(PrevisaoTempoServico tempo, PrevisaoRioServico rio) {
        this.tempo = tempo;
        this.rio = rio;
    }

    @GetMapping("/tempo")
    @Operation(summary = "Chuva, chance de chuva e temperatura dos próximos 7 dias",
            description = "Fonte: Open-Meteo, atualizado a cada 1 hora. Chuva também explicada em baldes por m².")
    public RespostaComFonte<PrevisaoTempo> tempo() {
        return tempo.consultar();
    }

    @GetMapping("/rio")
    @Operation(summary = "Vazão do rio: últimos 30 dias e previsão de 30 dias",
            description = "Fonte: GloFAS (Copernicus) via Open-Meteo, atualizado a cada 6 horas. "
                    + "Inclui tendência em 7 dias e comparação com o normal da época (anos desde 1997).")
    public RespostaComFonte<PrevisaoRio> rio() {
        return rio.consultar();
    }
}
