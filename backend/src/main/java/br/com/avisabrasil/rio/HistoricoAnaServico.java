package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.integracoes.ana.AnaClient;
import br.com.avisabrasil.integracoes.ana.SessaoAna;
import br.com.avisabrasil.previsao.SerieHistorica;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Histórico das réguas da ANA (leituras manuais desde os anos 1970/80), baixado aos poucos:
 * um ano por pedido (a API aceita até 366 dias), com pausa entre os pedidos. Cada ano fica
 * salvo em disco; anos fechados não são baixados de novo. O ano atual e o anterior são
 * atualizados uma vez por dia, porque a ANA publica as leituras manuais com atraso.
 */
@Service
public class HistoricoAnaServico {

    private static final Logger log = LoggerFactory.getLogger(HistoricoAnaServico.class);
    private static final Duration REATUALIZAR_ANOS_RECENTES = Duration.ofDays(1);
    private static final Duration ESPERA_APOS_ERRO = Duration.ofMinutes(5);

    /** @param anosBaixados quantos anos já estão salvos; anosTotal quantos existem no período */
    public record Progresso(int anosBaixados, int anosTotal) {
        public boolean completo() {
            return anosBaixados >= anosTotal;
        }
    }

    private final AnaClient ana;
    private final AvisaBrasilProperties.Ana config;
    private final Path pasta;
    private final Clock relogio;
    private final Map<Integer, TreeMap<LocalDate, Double>> series = new ConcurrentHashMap<>();
    private volatile Instant esperarAte = Instant.EPOCH;

    public HistoricoAnaServico(AnaClient ana, AvisaBrasilProperties propriedades, Clock relogio) {
        this.ana = ana;
        this.config = propriedades.ana();
        this.pasta = propriedades.fontes().pastaCache().resolve("ana");
        this.relogio = relogio;
        for (AvisaBrasilProperties.Estacao estacao : estacoes()) {
            series.put(estacao.codigo(), carregarDoDisco(estacao));
        }
    }

    public List<AvisaBrasilProperties.Estacao> estacoes() {
        return List.of(config.principal(), config.cidade());
    }

    /** Baixa no máximo um ano pendente. Chamado periodicamente pelo agendador. */
    public void baixarProximoAno() {
        if (!ana.habilitado() || relogio.instant().isBefore(esperarAte)) {
            return;
        }
        for (AvisaBrasilProperties.Estacao estacao : estacoes()) {
            Optional<Integer> ano = proximoAnoPendente(estacao);
            if (ano.isPresent()) {
                baixar(estacao, ano.get());
                return;
            }
        }
    }

    public SerieHistorica serie(int codigoEstacao) {
        TreeMap<LocalDate, Double> serie = series.getOrDefault(codigoEstacao, new TreeMap<>());
        synchronized (serie) {
            return new SerieHistorica(serie);
        }
    }

    public Progresso progresso(AvisaBrasilProperties.Estacao estacao) {
        int total = anoAtual() - estacao.inicioHistorico() + 1;
        int baixados = 0;
        for (int ano = estacao.inicioHistorico(); ano <= anoAtual(); ano++) {
            if (Files.exists(arquivo(estacao, ano))) {
                baixados++;
            }
        }
        return new Progresso(baixados, total);
    }

    /** Anos mais recentes primeiro, para as cheias e secas recentes aparecerem logo. */
    Optional<Integer> proximoAnoPendente(AvisaBrasilProperties.Estacao estacao) {
        for (int ano = anoAtual(); ano >= estacao.inicioHistorico(); ano--) {
            Path arquivo = arquivo(estacao, ano);
            if (!Files.exists(arquivo)) {
                return Optional.of(ano);
            }
            if (ano >= anoAtual() - 1 && estaVelho(arquivo)) {
                return Optional.of(ano);
            }
        }
        return Optional.empty();
    }

    private void baixar(AvisaBrasilProperties.Estacao estacao, int ano) {
        LocalDate inicio = LocalDate.of(ano, 1, 1);
        LocalDate fim = ano == anoAtual() ? LocalDate.now(relogio) : LocalDate.of(ano, 12, 31);
        try {
            String json = ana.buscarCotas(estacao.codigo(), inicio, fim);
            Map<LocalDate, Double> diarias = AnaConversor.cotasDiarias(json); // valida antes de salvar
            salvar(arquivo(estacao, ano), json);
            TreeMap<LocalDate, Double> serie = series.computeIfAbsent(estacao.codigo(), c -> new TreeMap<>());
            synchronized (serie) {
                serie.subMap(inicio, true, LocalDate.of(ano, 12, 31), true).clear();
                serie.putAll(diarias);
            }
            log.info("ANA: histórico da estação {} em {} baixado ({} dias)", estacao.codigo(), ano, diarias.size());
        } catch (SessaoAna.LoginAdiadoException e) {
            log.info("ANA: {}", e.getMessage());
            esperarAte = relogio.instant().plus(Duration.ofMinutes(1));
        } catch (RuntimeException e) {
            log.warn("ANA: falha ao baixar o ano {} da estação {} ({}). Nova tentativa em 5 min.",
                    ano, estacao.codigo(), e.getMessage());
            esperarAte = relogio.instant().plus(ESPERA_APOS_ERRO);
        }
    }

    private TreeMap<LocalDate, Double> carregarDoDisco(AvisaBrasilProperties.Estacao estacao) {
        TreeMap<LocalDate, Double> serie = new TreeMap<>();
        for (int ano = estacao.inicioHistorico(); ano <= anoAtual(); ano++) {
            Path arquivo = arquivo(estacao, ano);
            if (!Files.exists(arquivo)) {
                continue;
            }
            try {
                serie.putAll(AnaConversor.cotasDiarias(Files.readString(arquivo, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                log.warn("ANA: arquivo de histórico inválido {} ({}); será baixado de novo", arquivo, e.getMessage());
                try {
                    Files.deleteIfExists(arquivo);
                } catch (IOException ignorado) {
                    // tenta de novo no próximo início
                }
            }
        }
        return serie;
    }

    private boolean estaVelho(Path arquivo) {
        try {
            return Files.getLastModifiedTime(arquivo).toInstant().plus(REATUALIZAR_ANOS_RECENTES).isBefore(relogio.instant());
        } catch (IOException e) {
            return true;
        }
    }

    private void salvar(Path arquivo, String json) {
        try {
            Files.createDirectories(arquivo.toAbsolutePath().getParent());
            Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            Files.writeString(temporario, json, StandardCharsets.UTF_8);
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível salvar o histórico em disco: " + e.getMessage(), e);
        }
    }

    private Path arquivo(AvisaBrasilProperties.Estacao estacao, int ano) {
        return pasta.resolve("cotas-" + estacao.codigo() + "-" + ano + ".json");
    }

    private int anoAtual() {
        return LocalDate.now(relogio).getYear();
    }
}
