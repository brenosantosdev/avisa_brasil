package br.com.avisabrasil.integracoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheDeFonteTest {

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final Duration CINCO_MIN = Duration.ofMinutes(5);

    /** Relógio que o teste consegue adiantar. */
    private static class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-11-11T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel();

    private CacheDeFonte<Integer> novoCache(Path arquivo) {
        return new CacheDeFonte<>("Teste", UMA_HORA, CINCO_MIN, arquivo, Integer::parseInt, relogio);
    }

    @Test
    void semDadosFicaIndisponivelEPedeAtualizacao() {
        var cache = novoCache(null);
        assertThat(cache.situacao()).isEqualTo(Situacao.INDISPONIVEL);
        assertThat(cache.deveAtualizar()).isTrue();
    }

    @Test
    void sucessoFicaAtualizadoAteOIntervalo() {
        var cache = novoCache(null);
        cache.atualizar(() -> "42");

        assertThat(cache.valor()).contains(42);
        assertThat(cache.situacao()).isEqualTo(Situacao.ATUALIZADO);
        assertThat(cache.deveAtualizar()).isFalse();

        relogio.avancar(UMA_HORA);
        assertThat(cache.deveAtualizar()).isTrue();
    }

    @Test
    void falhaMantemUltimoDadoValidoComoDesatualizado() {
        var cache = novoCache(null);
        cache.atualizar(() -> "42");
        relogio.avancar(UMA_HORA);

        cache.atualizar(() -> {
            throw new IllegalStateException("sem internet");
        });

        assertThat(cache.valor()).contains(42);
        assertThat(cache.situacao()).isEqualTo(Situacao.DESATUALIZADO);
        assertThat(cache.ultimoErro()).hasValueSatisfying(e -> assertThat(e).contains("sem internet"));
        // espera 5 minutos antes de tentar de novo
        assertThat(cache.deveAtualizar()).isFalse();
        relogio.avancar(CINCO_MIN);
        assertThat(cache.deveAtualizar()).isTrue();
    }

    @Test
    void respostaInvalidaContaComoFalha() {
        var cache = novoCache(null);
        cache.atualizar(() -> "42");
        cache.atualizar(() -> "isto não é número");

        assertThat(cache.valor()).contains(42);
        assertThat(cache.situacao()).isEqualTo(Situacao.DESATUALIZADO);
    }

    @Test
    void dadoAntigoDemaisFicaDesatualizadoMesmoSemErro() {
        var cache = novoCache(null);
        cache.atualizar(() -> "42");
        relogio.avancar(UMA_HORA.multipliedBy(2).plusSeconds(1));

        assertThat(cache.situacao()).isEqualTo(Situacao.DESATUALIZADO);
    }

    @Test
    void sobreviveAoReinicioPeloDisco(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("sub").resolve("fonte.json");
        novoCache(arquivo).atualizar(() -> "42");
        assertThat(Files.readString(arquivo)).isEqualTo("42");

        var depoisDoReinicio = novoCache(arquivo);
        assertThat(depoisDoReinicio.valor()).contains(42);
    }

    @Test
    void falhaNaoApagaArquivoSalvo(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("fonte.json");
        var cache = novoCache(arquivo);
        cache.atualizar(() -> "42");
        cache.atualizar(() -> "lixo");

        assertThat(Files.readString(arquivo)).isEqualTo("42");
    }
}
