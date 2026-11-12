package br.com.avisabrasil.integracoes.ana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regras que evitam bloquear o IP na ANA: token reaproveitado e nada de login em sequência. */
class SessaoAnaTest {

    private static final Duration VALIDADE = Duration.ofMinutes(55);
    private static final Duration INTERVALO = Duration.ofMinutes(10);

    private static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-12T01:00:00Z");

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

    private final Relogio relogio = new Relogio();
    private final AtomicInteger logins = new AtomicInteger();

    private String login() {
        return "jwt-" + logins.incrementAndGet();
    }

    @Test
    void reaproveitaOTokenPor55Minutos() {
        var sessao = new SessaoAna(VALIDADE, INTERVALO, null, relogio);
        assertThat(sessao.token(this::login)).isEqualTo("jwt-1");
        relogio.avancar(Duration.ofMinutes(54));
        assertThat(sessao.token(this::login)).isEqualTo("jwt-1");
        assertThat(logins).hasValue(1);

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(sessao.token(this::login)).isEqualTo("jwt-2");
        assertThat(logins).hasValue(2);
    }

    @Test
    void naoFazLoginEmSequenciaMesmoComTokenRecusado() {
        var sessao = new SessaoAna(VALIDADE, INTERVALO, null, relogio);
        sessao.token(this::login);
        sessao.invalidar(); // a ANA respondeu 401

        assertThatThrownBy(() -> sessao.token(this::login)).isInstanceOf(SessaoAna.LoginAdiadoException.class);
        relogio.avancar(Duration.ofMinutes(9));
        assertThatThrownBy(() -> sessao.token(this::login)).isInstanceOf(SessaoAna.LoginAdiadoException.class);
        assertThat(logins).hasValue(1);

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(sessao.token(this::login)).isEqualTo("jwt-2");
    }

    @Test
    void loginQueFalhaTambemContaComoTentativa() {
        var sessao = new SessaoAna(VALIDADE, INTERVALO, null, relogio);
        assertThatThrownBy(() -> sessao.token(() -> {
            throw new IllegalStateException("Login na ANA recusado (HTTP 401)");
        })).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> sessao.token(this::login)).isInstanceOf(SessaoAna.LoginAdiadoException.class);
        assertThat(logins).hasValue(0);
    }

    @Test
    void reiniciarNaoGeraNovoLogin(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("ana-sessao.json");
        new SessaoAna(VALIDADE, INTERVALO, arquivo, relogio).token(this::login);

        var depoisDoReinicio = new SessaoAna(VALIDADE, INTERVALO, arquivo, relogio);
        assertThat(depoisDoReinicio.token(this::login)).isEqualTo("jwt-1");
        assertThat(logins).hasValue(1);
        // o arquivo guarda só token e horários
        assertThat(Files.readString(arquivo)).contains("tokenObtidoEm", "ultimaTentativaLogin").doesNotContain("senha");
    }

    @Test
    void reiniciarRespeitaIntervaloDaUltimaTentativa(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("ana-sessao.json");
        var sessao = new SessaoAna(VALIDADE, INTERVALO, arquivo, relogio);
        sessao.token(this::login);
        sessao.invalidar();

        var depoisDoReinicio = new SessaoAna(VALIDADE, INTERVALO, arquivo, relogio);
        assertThatThrownBy(() -> depoisDoReinicio.token(this::login)).isInstanceOf(SessaoAna.LoginAdiadoException.class);
    }

    @Test
    void usaOJwtDeTokenautenticacao() {
        String resposta = """
                {"status":"OK","code":200,"message":"Sucesso","items":{
                  "token":"0123456789abcdef0123456789abcdef","tokenautenticacao":"eyJ.cabecalho.assinatura",
                  "sucesso":true,"validade":"Wed Nov 11 22:34:53 GMT-03:00 2026"}}
                """;
        assertThat(AnaClient.extrairToken(resposta)).isEqualTo("eyJ.cabecalho.assinatura");
        assertThatThrownBy(() -> AnaClient.extrairToken("{\"items\":{\"token\":\"x\"}}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
