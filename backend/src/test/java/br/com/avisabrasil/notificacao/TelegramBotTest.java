package br.com.avisabrasil.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import br.com.avisabrasil.dominio.PedidoAjuda;
import br.com.avisabrasil.repositorio.InscritoTelegramRepositorio;
import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import br.com.avisabrasil.rio.Nivel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Conversa com o bot: /start, 2 (preciso de ajuda), localização, número de pessoas, 1 (estou bem), /parar. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class TelegramBotTest {

    private static final long CHAT = 555123L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TelegramBot bot;
    @Autowired
    private TelegramNotificador notificador;
    @Autowired
    private InscritoTelegramRepositorio inscritos;
    @Autowired
    private PedidoAjudaRepositorio pedidos;
    @MockitoBean
    private TelegramApi api;

    private String mensagem(String corpoDaMensagem) throws Exception {
        JsonNode atualizacao = JSON.readTree("""
                {"update_id": 1, "message": {"chat": {"id": %d}, "from": {"first_name": "Maria"}, %s}}
                """.formatted(CHAT, corpoDaMensagem));
        return bot.processar(atualizacao);
    }

    @Test
    void conversaCompleta() throws Exception {
        assertThat(mensagem("\"text\": \"/start\"")).isEqualTo(TelegramBot.BOAS_VINDAS);
        assertThat(inscritos.findByChatId(CHAT)).hasValueSatisfying(i -> {
            assertThat(i.isAtivo()).isTrue();
            assertThat(i.getNome()).isEqualTo("Maria");
        });

        // o alerta chega para quem se inscreveu, pedindo a resposta 1 ou 2
        notificador.enviar(new MensagemAlerta("t", "ALERTA – CHEIA", "c", Nivel.ALERTA, null, false, false, "x"));
        verify(api).enviarTexto(eq(CHAT), eq("ALERTA – CHEIA\n\nResponda: 1 = estou bem / 2 = preciso de ajuda"));

        assertThat(mensagem("\"text\": \"2\"")).isEqualTo(TelegramBot.PRECISO_DE_AJUDA);
        assertThat(mensagem("\"location\": {\"latitude\": -0.13, \"longitude\": -67.09}"))
                .startsWith("Localização recebida");
        assertThat(mensagem("\"text\": \"3 pessoas\"")).isEqualTo("Anotado: 3 pessoas.");
        assertThat(mensagem("\"text\": \"1\"")).isEqualTo(TelegramBot.ESTOU_BEM);

        var lista = pedidos.findTop100ByOrderByIdDesc();
        assertThat(lista).hasSize(2);
        PedidoAjuda ajuda = lista.get(1);
        assertThat(ajuda.getTipo()).isEqualTo(PedidoAjuda.Tipo.PRECISO_DE_AJUDA);
        assertThat(ajuda.getCanal()).isEqualTo(PedidoAjuda.Canal.TELEGRAM);
        assertThat(ajuda.getPessoas()).isEqualTo(3);
        assertThat(ajuda.getLatitude()).isEqualTo(-0.13);
        assertThat(ajuda.getNome()).isEqualTo("Maria");
        assertThat(lista.get(0).getTipo()).isEqualTo(PedidoAjuda.Tipo.ESTOU_BEM);

        assertThat(mensagem("\"text\": \"oi\"")).isEqualTo(TelegramBot.AJUDA);
        assertThat(mensagem("\"text\": \"/situacao\"")).contains("Normal");

        mensagem("\"text\": \"/parar\"");
        assertThat(inscritos.findByChatId(CHAT)).hasValueSatisfying(i -> assertThat(i.isAtivo()).isFalse());
        verify(api, org.mockito.Mockito.atLeastOnce()).enviarTexto(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}
