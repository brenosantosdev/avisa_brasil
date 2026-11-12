package br.com.avisabrasil.agendador;

import br.com.avisabrasil.mapa.MapaServico;
import br.com.avisabrasil.previsao.PrevisaoRioServico;
import br.com.avisabrasil.previsao.PrevisaoTempoServico;
import br.com.avisabrasil.risco.MotorDeRisco;
import br.com.avisabrasil.rio.HistoricoAnaServico;
import br.com.avisabrasil.rio.RioAgoraServico;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere a cada minuto se alguma fonte precisa ser atualizada. Cada cache decide sozinho
 * (Open-Meteo a cada 1 hora, GloFAS a cada 6 horas, histórico e OpenStreetMap uma vez por dia,
 * e nova tentativa 5 minutos depois de uma falha). Depois, o motor de risco reavalia o nível.
 */
@Component
@ConditionalOnProperty(name = "avisabrasil.agendador.habilitado", havingValue = "true", matchIfMissing = true)
public class AtualizadorDeFontes {

    private final PrevisaoTempoServico tempo;
    private final PrevisaoRioServico rio;
    private final MapaServico mapa;
    private final RioAgoraServico rioAgora;
    private final HistoricoAnaServico historicoAna;
    private final MotorDeRisco motor;

    public AtualizadorDeFontes(PrevisaoTempoServico tempo, PrevisaoRioServico rio, MapaServico mapa,
            RioAgoraServico rioAgora, HistoricoAnaServico historicoAna, MotorDeRisco motor) {
        this.tempo = tempo;
        this.rio = rio;
        this.mapa = mapa;
        this.rioAgora = rioAgora;
        this.historicoAna = historicoAna;
        this.motor = motor;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void verificar() {
        tempo.atualizarSeNecessario();
        rio.atualizarSeNecessario();
        mapa.atualizarSeNecessario();
        rioAgora.atualizarSeNecessario();
        motor.avaliar();
    }

    /** Baixa um ano do histórico da ANA por vez, com pausa entre os pedidos. */
    @Scheduled(initialDelay = 30_000, fixedDelayString = "${avisabrasil.ana.pausa-historico}")
    public void baixarHistoricoAna() {
        historicoAna.baixarProximoAno();
    }
}
