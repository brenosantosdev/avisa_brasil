package br.com.avisabrasil.previsao;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.CacheDeFonte;
import br.com.avisabrasil.integracoes.GlofasClient;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Mantém em cache a previsão de vazão do GloFAS (a cada 6 horas) e o histórico desde 1997
 * (uma vez por dia), e junta os dois na {@link PrevisaoRio}.
 */
@Service
public class PrevisaoRioServico {

    private final GlofasClient cliente;
    private final AvisaBrasilProperties propriedades;
    private final Clock relogio;
    private final CacheDeFonte<SerieGlofas> previsao;
    private final CacheDeFonte<SerieHistorica> historico;

    public PrevisaoRioServico(GlofasClient cliente, AvisaBrasilProperties propriedades, Clock relogio) {
        this.cliente = cliente;
        this.propriedades = propriedades;
        this.relogio = relogio;
        var fontes = propriedades.fontes();
        this.previsao = new CacheDeFonte<>(
                GlofasClient.FONTE,
                fontes.glofas().intervalo(),
                fontes.novaTentativa(),
                fontes.pastaCache().resolve("glofas-previsao.json"),
                GlofasConversor::converterPrevisao,
                relogio);
        this.historico = new CacheDeFonte<>(
                GlofasClient.FONTE + " (histórico)",
                fontes.glofasHistorico().intervalo(),
                fontes.novaTentativa(),
                fontes.pastaCache().resolve("glofas-historico.json"),
                GlofasConversor::converterHistorico,
                relogio);
    }

    public void atualizarSeNecessario() {
        if (previsao.deveAtualizar()) {
            atualizarPrevisao();
        }
        if (historico.deveAtualizar()) {
            atualizarHistorico();
        }
    }

    public void atualizarPrevisao() {
        previsao.atualizar(cliente::buscarPrevisao);
    }

    public void atualizarHistorico() {
        LocalDate ontem = LocalDate.now(relogio).minusDays(1);
        historico.atualizar(() -> cliente.buscarHistorico(ontem));
    }

    public RespostaComFonte<PrevisaoRio> consultar() {
        PrevisaoRio dados = previsao.valor()
                .map(serie -> AnaliseRio.analisar(
                        propriedades.comunidade().rio(),
                        serie,
                        historico.valor().orElse(null),
                        historico.situacao(),
                        LocalDate.now(relogio)))
                .orElse(null);
        return new RespostaComFonte<>(
                GlofasClient.FONTE,
                GlofasClient.FONTE_URL,
                previsao.obtidoEm().map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                previsao.situacao(),
                previsao.ultimoErro().orElse(null),
                dados);
    }
}
