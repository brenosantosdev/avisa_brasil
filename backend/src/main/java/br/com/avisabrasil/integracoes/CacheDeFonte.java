package br.com.avisabrasil.integracoes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda a última resposta válida de uma fonte externa.
 *
 * <ul>
 *   <li>Se a busca falhar, o último dado válido continua disponível, marcado como desatualizado.</li>
 *   <li>A resposta bruta é salva em disco, para sobreviver a reinícios sem internet.</li>
 *   <li>Depois de uma falha, só tenta de novo após o tempo de "nova tentativa".</li>
 * </ul>
 */
public class CacheDeFonte<T> {

    private static final Logger log = LoggerFactory.getLogger(CacheDeFonte.class);

    private record Estado<T>(T valor, Instant obtidoEm, Instant ultimaTentativa, String ultimoErro) {
    }

    private final String nome;
    private final Duration intervalo;
    private final Duration novaTentativa;
    private final Path arquivo;
    private final Function<String, T> conversor;
    private final Clock relogio;
    private volatile Estado<T> estado = new Estado<>(null, null, null, null);

    /**
     * @param arquivo onde salvar a última resposta válida; {@code null} para não salvar em disco
     */
    public CacheDeFonte(String nome, Duration intervalo, Duration novaTentativa, Path arquivo,
            Function<String, T> conversor, Clock relogio) {
        this.nome = nome;
        this.intervalo = intervalo;
        this.novaTentativa = novaTentativa;
        this.arquivo = arquivo;
        this.conversor = conversor;
        this.relogio = relogio;
        carregarDoDisco();
    }

    /** Busca a resposta bruta (JSON) e, se for válida, guarda. Nunca lança exceção. */
    public void atualizar(Supplier<String> busca) {
        Instant agora = relogio.instant();
        try {
            String json = busca.get();
            T valor = conversor.apply(json);
            estado = new Estado<>(valor, agora, agora, null);
            salvarNoDisco(json);
            log.info("{}: dados atualizados", nome);
        } catch (RuntimeException e) {
            String erro = e.getClass().getSimpleName() + ": " + e.getMessage();
            Estado<T> anterior = estado;
            estado = new Estado<>(anterior.valor(), anterior.obtidoEm(), agora, erro);
            log.warn("{}: falha ao atualizar ({}). Mantendo o último dado válido.", nome, erro);
        }
    }

    /** Diz se já é hora de buscar de novo. */
    public boolean deveAtualizar() {
        Estado<T> e = estado;
        Instant agora = relogio.instant();
        if (e.valor() != null && e.ultimoErro() == null) {
            return !agora.isBefore(e.obtidoEm().plus(intervalo));
        }
        if (e.ultimaTentativa() == null) {
            return true;
        }
        return !agora.isBefore(e.ultimaTentativa().plus(novaTentativa));
    }

    public Situacao situacao() {
        Estado<T> e = estado;
        if (e.valor() == null) {
            return Situacao.INDISPONIVEL;
        }
        boolean passouDoPrazo = relogio.instant().isAfter(e.obtidoEm().plus(intervalo.multipliedBy(2)));
        if (e.ultimoErro() != null || passouDoPrazo) {
            return Situacao.DESATUALIZADO;
        }
        return Situacao.ATUALIZADO;
    }

    public Optional<T> valor() {
        return Optional.ofNullable(estado.valor());
    }

    public Optional<Instant> obtidoEm() {
        return Optional.ofNullable(estado.obtidoEm());
    }

    public Optional<String> ultimoErro() {
        return Optional.ofNullable(estado.ultimoErro());
    }

    private void carregarDoDisco() {
        if (arquivo == null || !Files.exists(arquivo)) {
            return;
        }
        try {
            String json = Files.readString(arquivo, StandardCharsets.UTF_8);
            T valor = conversor.apply(json);
            Instant salvoEm = Files.getLastModifiedTime(arquivo).toInstant();
            estado = new Estado<>(valor, salvoEm, null, null);
            log.info("{}: último dado válido carregado do disco (salvo em {})", nome, salvoEm);
        } catch (IOException | RuntimeException e) {
            log.warn("{}: não foi possível ler o cache em disco {} ({})", nome, arquivo, e.getMessage());
        }
    }

    private void salvarNoDisco(String json) {
        if (arquivo == null) {
            return;
        }
        try {
            Files.createDirectories(arquivo.toAbsolutePath().getParent());
            Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            Files.writeString(temporario, json, StandardCharsets.UTF_8);
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("{}: não foi possível salvar o cache em disco ({})", nome, e.getMessage());
        }
    }
}
