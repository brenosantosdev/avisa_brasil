package br.com.avisabrasil.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.dominio.EnvioAlerta;
import br.com.avisabrasil.repositorio.EnvioAlertaRepositorio;
import br.com.avisabrasil.risco.AvaliacaoDeRisco;
import br.com.avisabrasil.risco.Fator;
import br.com.avisabrasil.risco.FonteRisco;
import br.com.avisabrasil.risco.TipoRisco;
import br.com.avisabrasil.rio.Nivel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MensagensEAlertasTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("America/Manaus"));
    private final MontadorDeMensagem montador = new MontadorDeMensagem(new ObjectMapper(), RELOGIO);

    private static Alerta alerta(Alerta.Evento evento, Nivel nivel, TipoRisco tipo, FonteRisco fonte, boolean simulado) {
        return new Alerta(evento, nivel, tipo, fonte, simulado, "texto", RELOGIO.instant());
    }

    private static AvaliacaoDeRisco avaliacao(Fator principal) {
        return new AvaliacaoDeRisco(principal.nivel(), principal.tipo(), principal, List.of(principal), null, null, null,
                null, OffsetDateTime.now(RELOGIO), principal.simulado());
    }

    /* ---------- Texto das mensagens ---------- */

    @Test
    void alertaSimuladoTemMarcacaoNivelMotivoAcoesEFonte() {
        Fator f = new Fator(FonteRisco.SENSOR, TipoRisco.CHEIA, Nivel.EMERGENCIA, Fator.Motivo.COTA_CHEIA,
                Map.of("cotaCm", 1480.0, "limiteCm", 1473.0), true, false);
        var m = montador.montar(alerta(Alerta.Evento.NIVEL_SUBIU, Nivel.EMERGENCIA, TipoRisco.CHEIA, FonteRisco.SENSOR, true), avaliacao(f));

        assertThat(m.titulo()).isEqualTo("[SIMULAÇÃO] 🔴 AvisaBrasil: EMERGÊNCIA – CHEIA");
        assertThat(m.texto())
                .startsWith("[SIMULAÇÃO] Isto é uma demonstração, não é medição real.")
                .contains("🔴 EMERGÊNCIA – CHEIA")
                .contains("O que está acontecendo: O rio está em 14,80 m, acima da cota de Emergência de cheia (14,73 m).")
                .contains("O que fazer agora:\n• Saia agora para um lugar seguro e alto.")
                .contains("Fonte: sensor SIMULADO. AvisaBrasil, 12/11 às 08:00.");
        assertThat(m.textoCurto()).startsWith("[SIMULAÇÃO] AvisaBrasil: EMERGÊNCIA – CHEIA.");
        assertThat(m.prioridade()).isEqualTo(5);
    }

    @Test
    void alertaRealDeSecaTemOrientacoesDeSeca() {
        Fator f = new Fator(FonteRisco.ANA, TipoRisco.SECA, Nivel.ATENCAO, Fator.Motivo.SECA_EPOCA,
                Map.of("cotaCm", 774.0, "limiteCm", 821, "percentual", 25, "anoInicial", 1978, "anoFinal", 2025), false, false);
        var m = montador.montar(alerta(Alerta.Evento.NIVEL_SUBIU, Nivel.ATENCAO, TipoRisco.SECA, FonteRisco.ANA, false), avaliacao(f));

        assertThat(m.texto()).doesNotContain("SIMULAÇÃO")
                .contains("🟡 ATENÇÃO – SECA")
                .contains("• Água potável: guarde água limpa em vasilhas tampadas.")
                .contains("Fonte: medição da ANA (régua do Rio Negro).");
        // só as 3 primeiras orientações, para a mensagem ficar curta
        assertThat(m.texto().lines().filter(l -> l.startsWith("• "))).hasSize(3);
    }

    @Test
    void situacaoNormalizada() {
        var m = montador.montar(alerta(Alerta.Evento.NORMALIZADO, Nivel.NORMAL, null, null, false), null);
        assertThat(m.titulo()).isEqualTo("🟢 AvisaBrasil: Situação normalizada");
        assertThat(m.texto()).contains("O rio voltou ao nível normal.");
        assertThat(TelegramNotificador.textoComResposta(m)).doesNotContain("Responda");
    }

    @Test
    void ntfyEmJsonComPrioridadeETags() {
        var m = new MensagemAlerta("título", "texto", "curto", Nivel.ALERTA, TipoRisco.CHEIA, true, false, "x");
        var corpo = NtfyNotificador.corpo("topico-secreto", m);
        assertThat(corpo).containsEntry("topic", "topico-secreto").containsEntry("priority", 4)
                .containsEntry("title", "título").containsEntry("tags", List.of("warning", "construction"));
    }

    @Test
    void smsCurtoECortadoNoLimite() {
        var m = new MensagemAlerta("t", "longo", "x".repeat(1000), Nivel.ALERTA, TipoRisco.CHEIA, false, false, "x");
        var form = TwilioSmsNotificador.formulario("+15550001111", "+5592999990000", m);
        assertThat(form.getFirst("To")).isEqualTo("+5592999990000");
        assertThat(form.getFirst("Body")).hasSize(TwilioSmsNotificador.MAXIMO_CARACTERES).endsWith("…");
    }

    @Test
    void telegramPedeResposta1Ou2NosAlertas() {
        var m = new MensagemAlerta("t", "texto do alerta", "c", Nivel.ALERTA, TipoRisco.CHEIA, false, false, "x");
        assertThat(TelegramNotificador.textoComResposta(m)).endsWith("Responda: 1 = estou bem / 2 = preciso de ajuda");
    }

    /* ---------- Envio pelos canais ---------- */

    /** Canal falso que guarda o que recebeu. */
    private static class CanalFalso implements Notificador {
        final Canal canal;
        final boolean ligado;
        final boolean falha;
        final List<MensagemAlerta> recebidas = new ArrayList<>();

        CanalFalso(Canal canal, boolean ligado, boolean falha) {
            this.canal = canal;
            this.ligado = ligado;
            this.falha = falha;
        }

        public Canal canal() { return canal; }
        public boolean habilitado() { return ligado; }
        public String motivoDesligado() { return ligado ? null : "desligado no teste"; }
        public void enviar(MensagemAlerta m) {
            if (falha) {
                throw new IllegalStateException("ntfy falhou (HTTP 500)");
            }
            recebidas.add(m);
        }
    }

    private static EnvioAlertaRepositorio repositorioEmMemoria(List<EnvioAlerta> salvos) {
        EnvioAlertaRepositorio repo = mock(EnvioAlertaRepositorio.class);
        when(repo.save(any())).thenAnswer(inv -> {
            salvos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repo.findFirstByCanalAndStatusOrderByIdDesc(any(), any())).thenAnswer(inv -> salvos.stream()
                .filter(e -> e.getCanal() == inv.getArgument(0) && e.getStatus() == inv.getArgument(1))
                .reduce((a, b) -> b));
        return repo;
    }

    @Test
    void naoReenviaOMesmoAlertaEmSequencia() {
        List<EnvioAlerta> salvos = new ArrayList<>();
        CanalFalso telegram = new CanalFalso(Canal.TELEGRAM, true, false);
        CanalFalso ntfyComFalha = new CanalFalso(Canal.NTFY, true, true);
        CanalFalso smsDesligado = new CanalFalso(Canal.SMS, false, false);
        var servico = new ServicoDeAlertas(List.of(telegram, ntfyComFalha, smsDesligado), montador,
                repositorioEmMemoria(salvos), RELOGIO);

        Alerta secaA = alerta(Alerta.Evento.NIVEL_SUBIU, Nivel.ATENCAO, TipoRisco.SECA, FonteRisco.ANA, false);
        Alerta secaB = alerta(Alerta.Evento.NIVEL_SUBIU, Nivel.ATENCAO, TipoRisco.SECA, FonteRisco.ANA, false);
        Alerta cheia = alerta(Alerta.Evento.NIVEL_SUBIU, Nivel.ALERTA, TipoRisco.CHEIA, FonteRisco.SENSOR, true);

        assertThat(servico.enviar(secaA, null)).extracting(EnvioAlerta::getStatus)
                .containsExactly(EnvioAlerta.Status.FALHOU, EnvioAlerta.Status.ENVIADO); // ntfy falhou, Telegram foi
        // mesmo conteúdo logo em seguida: o Telegram não recebe de novo
        servico.enviar(secaB, null);
        assertThat(telegram.recebidas).hasSize(1);
        assertThat(salvos.get(salvos.size() - 1).getStatus()).isEqualTo(EnvioAlerta.Status.IGNORADO_REPETIDO);
        // conteúdo diferente: envia; e depois o primeiro conteúdo de novo (não é mais "em sequência"): envia
        servico.enviar(cheia, null);
        servico.enviar(secaA, null);
        assertThat(telegram.recebidas).hasSize(3);
        // o canal desligado não aparece no histórico; a falha guarda o motivo sem segredos
        assertThat(salvos).noneMatch(e -> e.getCanal() == Canal.SMS);
        assertThat(salvos).filteredOn(e -> e.getStatus() == EnvioAlerta.Status.FALHOU)
                .allMatch(e -> e.getErro().equals("ntfy falhou (HTTP 500)"));
    }

    @Test
    void erroSeguroNaoMostraUrlNemToken() {
        var e = new org.springframework.web.client.ResourceAccessException(
                "I/O error on GET request for \"https://api.telegram.org/bot123:SEGREDO/getUpdates\"");
        assertThat(ErroSeguro.descrever(e)).isEqualTo("ResourceAccessException").doesNotContain("SEGREDO");
    }
}
