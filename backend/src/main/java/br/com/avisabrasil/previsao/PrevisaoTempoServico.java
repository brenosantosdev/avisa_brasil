package br.com.avisabrasil.previsao;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.CacheDeFonte;
import br.com.avisabrasil.integracoes.OpenMeteoClient;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Mantém a previsão do tempo do Open-Meteo em cache (atualiza a cada 1 hora). */
@Service
public class PrevisaoTempoServico {

    private final OpenMeteoClient cliente;
    private final CacheDeFonte<PrevisaoTempo> cache;
    private final Clock relogio;

    public PrevisaoTempoServico(OpenMeteoClient cliente, AvisaBrasilProperties propriedades, Clock relogio) {
        this.cliente = cliente;
        this.relogio = relogio;
        var fontes = propriedades.fontes();
        this.cache = new CacheDeFonte<>(
                OpenMeteoClient.FONTE,
                fontes.openMeteo().intervalo(),
                fontes.novaTentativa(),
                fontes.pastaCache().resolve("open-meteo-previsao.json"),
                OpenMeteoConversor::converter,
                relogio);
    }

    public void atualizarSeNecessario() {
        if (cache.deveAtualizar()) {
            atualizar();
        }
    }

    public void atualizar() {
        cache.atualizar(cliente::buscarPrevisao);
    }

    public RespostaComFonte<PrevisaoTempo> consultar() {
        return new RespostaComFonte<>(
                OpenMeteoClient.FONTE,
                OpenMeteoClient.FONTE_URL,
                cache.obtidoEm().map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                cache.situacao(),
                cache.ultimoErro().orElse(null),
                cache.valor().orElse(null));
    }
}
