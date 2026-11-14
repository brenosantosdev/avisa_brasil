package br.com.avisabrasil.web.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AcessoPainelTest {

    private static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-13T12:00:00Z");

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

    @Test
    void semSenhaConfiguradaOPainelFicaTrancado() {
        var acesso = new AcessoPainel("", new Relogio());
        assertThat(acesso.senhaConfigurada()).isFalse();
        assertThat(acesso.verificar("", "127.0.0.1")).isEqualTo(AcessoPainel.Resultado.PAINEL_TRANCADO);
        assertThat(acesso.verificar(null, "127.0.0.1")).isEqualTo(AcessoPainel.Resultado.PAINEL_TRANCADO);
    }

    @Test
    void bloqueiaDepoisDeCincoSenhasErradas() {
        var relogio = new Relogio();
        var acesso = new AcessoPainel("certa", relogio);
        for (int i = 0; i < 4; i++) {
            assertThat(acesso.verificar("errada", "10.0.0.1")).isEqualTo(AcessoPainel.Resultado.SENHA_ERRADA);
        }
        assertThat(acesso.verificar("errada", "10.0.0.1")).isEqualTo(AcessoPainel.Resultado.BLOQUEADO);
        // bloqueado: nem a senha certa entra durante 15 minutos
        assertThat(acesso.verificar("certa", "10.0.0.1")).isEqualTo(AcessoPainel.Resultado.BLOQUEADO);
        // outro endereço não é afetado
        assertThat(acesso.verificar("certa", "10.0.0.2")).isEqualTo(AcessoPainel.Resultado.OK);

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(15));
        assertThat(acesso.verificar("certa", "10.0.0.1")).isEqualTo(AcessoPainel.Resultado.OK);
    }

    @Test
    void quaisRotasSaoProtegidas() {
        assertThat(ProtecaoDoPainel.protegido("POST", "/api/pedidos-ajuda")).isFalse();
        assertThat(ProtecaoDoPainel.protegido("GET", "/api/pedidos-ajuda")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("PATCH", "/api/pedidos-ajuda/3/atendido")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("GET", "/api/pontos-seguros")).isFalse();
        assertThat(ProtecaoDoPainel.protegido("POST", "/api/pontos-seguros")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("DELETE", "/api/pontos-seguros/2")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("POST", "/api/simulacao/limpar")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("POST", "/api/alertas/manual")).isTrue();
        assertThat(ProtecaoDoPainel.protegido("POST", "/api/painel/entrar")).isFalse();
        assertThat(ProtecaoDoPainel.protegido("GET", "/api/painel/eventos")).isTrue();
    }
}
