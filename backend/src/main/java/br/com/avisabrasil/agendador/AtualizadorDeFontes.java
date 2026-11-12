package br.com.avisabrasil.agendador;

import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.PrevisaoTempoServico;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere a cada minuto se alguma fonte precisa ser atualizada. Cada cache decide sozinho
 * (Open-Meteo a cada 1 hora, GloFAS a cada 6 horas, histórico uma vez por dia, e
 * nova tentativa 5 minutos depois de uma falha).
 */
@Component
@ConditionalOnProperty(name = "avisabrasil.agendador.habilitado", havingValue = "true", matchIfMissing = true)
public class AtualizadorDeFontes {

    private final PrevisaoTempoServico tempo;
    private final PrevisaoRioServico rio;

    public AtualizadorDeFontes(PrevisaoTempoServico tempo, PrevisaoRioServico rio) {
        this.tempo = tempo;
        this.rio = rio;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void verificar() {
        tempo.atualizarSeNecessario();
        rio.atualizarSeNecessario();
    }
}
