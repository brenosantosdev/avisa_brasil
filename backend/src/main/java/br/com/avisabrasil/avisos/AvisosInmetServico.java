package br.com.avisabrasil.avisos;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.CacheDeFonte;
import br.com.avisabrasil.integracoes.InmetClient;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Mantém os avisos do INMET em cache (a cada 30 minutos) e separa os ativos dos já encerrados. */
@Service
public class AvisosInmetServico {

    private final InmetClient cliente;
    private final CacheDeFonte<List<AvisoInmet>> cache;
    private final Clock relogio;
    private final AvisaBrasilProperties.Inmet config;
    private final String municipio;

    public AvisosInmetServico(InmetClient cliente, AvisaBrasilProperties propriedades, Clock relogio) {
        this.cliente = cliente;
        this.relogio = relogio;
        this.config = propriedades.fontes().inmet();
        this.municipio = propriedades.comunidade().nome();
        this.cache = new CacheDeFonte<>(
                InmetClient.FONTE,
                config.intervalo(),
                propriedades.fontes().novaTentativa(),
                propriedades.fontes().pastaCache().resolve("inmet-avisos.json"),
                json -> InmetConversor.converter(json, config.codigoIbge()),
                relogio);
    }

    public void atualizarSeNecessario() {
        if (cache.deveAtualizar()) {
            cache.atualizar(cliente::buscarAvisos);
        }
    }

    public RespostaComFonte<AvisosInmet> consultar() {
        AvisosInmet avisos = cache.valor()
                .map(lista -> separar(lista, relogio.instant(), config.diasRecentes()))
                .map(s -> new AvisosInmet(municipio, config.codigoIbge(), s.ativos(), s.recentes(), config.diasRecentes()))
                .orElse(null);
        return new RespostaComFonte<>(
                InmetClient.FONTE,
                InmetClient.FONTE_URL,
                cache.obtidoEm().map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                cache.situacao(),
                cache.ultimoErro().orElse(null),
                avisos);
    }

    /** Ativos: ainda não terminaram (inclui os que começam depois). Recentes: terminaram há até {@code dias} dias. */
    record Separados(List<AvisoInmet> ativos, List<AvisoInmet> recentes) {
    }

    static Separados separar(List<AvisoInmet> avisos, Instant agora, int dias) {
        Instant limite = agora.minus(Duration.ofDays(dias));
        List<AvisoInmet> ativos = avisos.stream()
                .filter(a -> a.fim().toInstant().isAfter(agora))
                .sorted(Comparator.comparing(AvisoInmet::inicio))
                .toList();
        List<AvisoInmet> recentes = avisos.stream()
                .filter(a -> !a.fim().toInstant().isAfter(agora) && a.fim().toInstant().isAfter(limite))
                .sorted(Comparator.comparing(AvisoInmet::inicio).reversed())
                .toList();
        return new Separados(ativos, recentes);
    }
}
