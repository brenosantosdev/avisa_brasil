package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.dominio.InscritoTelegram;
import br.com.avisabrasil.dominio.PedidoAjuda;
import br.com.avisabrasil.repositorio.InscritoTelegramRepositorio;
import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import br.com.avisabrasil.risco.AvaliacaoDeRisco;
import br.com.avisabrasil.risco.MensagensDeRisco;
import br.com.avisabrasil.risco.MotorDeRisco;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Bot do Telegram por long polling (funciona em localhost, sem webhook nem endereço público).
 *
 * <ul>
 *   <li>/start: passa a receber os alertas; /parar: deixa de receber; /situacao: nível atual.</li>
 *   <li>1 = estou bem; 2 = preciso de ajuda (vira pedido no painel da Defesa Civil).</li>
 *   <li>Depois do 2: a localização enviada e "3 pessoas" completam o pedido.</li>
 * </ul>
 */
@Component
public class TelegramBot implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);
    /** Localização e número de pessoas completam o pedido feito até este tempo antes. */
    static final Duration JANELA_DO_PEDIDO = Duration.ofHours(2);
    private static final Pattern PESSOAS = Pattern.compile("^(\\d{1,3})\\s*pessoas?$", Pattern.CASE_INSENSITIVE);

    static final String BOAS_VINDAS = """
            Olá! Você vai receber os alertas de cheia e seca do AvisaBrasil.
            Quando chegar um alerta, responda:
            1 = estou bem
            2 = preciso de ajuda
            Para ver a situação agora, envie /situacao. Para parar de receber, envie /parar.""";
    static final String AJUDA = """
            Comandos do AvisaBrasil:
            1 = estou bem
            2 = preciso de ajuda
            /situacao = nível agora
            /parar = parar de receber alertas
            Em emergência, ligue 193 (Bombeiros) ou 199 (Defesa Civil).""";
    static final String ESTOU_BEM = "Obrigado! Registramos que você está bem.";
    static final String PRECISO_DE_AJUDA = """
            Pedido de ajuda registrado. A Defesa Civil vai ver no painel.
            Para ajudar a equipe a chegar até você:
            • envie sua localização (clipe 📎 > Localização)
            • diga quantas pessoas, por exemplo: 3 pessoas
            Se for urgente, ligue 193 (Bombeiros) ou 199 (Defesa Civil).""";

    private final TelegramApi api;
    private final InscritoTelegramRepositorio inscritos;
    private final PedidoAjudaRepositorio pedidos;
    private final MotorDeRisco motor;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final boolean ligado;
    private volatile boolean rodando;
    private Thread linha;
    private long proximoOffset;

    public TelegramBot(TelegramApi api, InscritoTelegramRepositorio inscritos, PedidoAjudaRepositorio pedidos,
            MotorDeRisco motor, TransactionTemplate transacao, Clock relogio,
            @Value("${avisabrasil.telegram.polling:true}") boolean polling) {
        this.api = api;
        this.inscritos = inscritos;
        this.pedidos = pedidos;
        this.motor = motor;
        this.transacao = transacao;
        this.relogio = relogio;
        this.ligado = polling && api.habilitado();
    }

    /* ---------- Ciclo de vida: uma linha de execução só para o long polling ---------- */

    @Override
    public void start() {
        if (!ligado) {
            log.info("Telegram: bot desligado (defina TELEGRAM_BOT_TOKEN)");
            return;
        }
        rodando = true;
        linha = new Thread(this::laco, "telegram-bot");
        linha.setDaemon(true);
        linha.start();
        log.info("Telegram: bot ligado (long polling)");
    }

    @Override
    public void stop() {
        rodando = false;
        if (linha != null) {
            linha.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return rodando;
    }

    private void laco() {
        long espera = 5;
        while (rodando) {
            try {
                JsonNode resposta = api.buscarAtualizacoes(proximoOffset);
                for (JsonNode atualizacao : resposta.path("result")) {
                    proximoOffset = Math.max(proximoOffset, atualizacao.path("update_id").asLong() + 1);
                    try {
                        processar(atualizacao);
                    } catch (RuntimeException e) {
                        log.warn("Telegram: falha ao tratar uma mensagem ({})", ErroSeguro.descrever(e));
                    }
                }
                espera = 5;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    log.warn("Telegram: o token foi recusado (HTTP 401). Bot parado; confira TELEGRAM_BOT_TOKEN.");
                    rodando = false;
                    return;
                }
                log.warn("Telegram: falha no long polling ({}); nova tentativa em {} s", ErroSeguro.descrever(e), espera);
                espera = dormir(espera);
            } catch (RestClientException e) {
                if (!rodando) {
                    return;
                }
                log.warn("Telegram: falha no long polling ({}); nova tentativa em {} s", ErroSeguro.descrever(e), espera);
                espera = dormir(espera);
            }
        }
    }

    private long dormir(long segundos) {
        try {
            Thread.sleep(segundos * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rodando = false;
        }
        return Math.min(segundos * 2, 120);
    }

    /* ---------- Tratamento das mensagens ---------- */

    /** Trata uma atualização do Telegram e responde na mesma conversa. Devolve a resposta enviada. */
    public String processar(JsonNode atualizacao) {
        JsonNode mensagem = atualizacao.path("message");
        if (mensagem.isMissingNode()) {
            return null;
        }
        long chatId = mensagem.path("chat").path("id").asLong();
        String nome = mensagem.path("from").path("first_name").asText(mensagem.path("chat").path("title").asText(""));
        String resposta = transacao.execute(s -> responder(chatId, nome, mensagem));
        if (resposta != null) {
            try {
                api.enviarTexto(chatId, resposta);
            } catch (RestClientException e) {
                log.warn("Telegram: não foi possível responder ({})", ErroSeguro.descrever(e));
            }
        }
        return resposta;
    }

    private String responder(long chatId, String nome, JsonNode mensagem) {
        Instant agora = relogio.instant();
        String contato = Long.toString(chatId);

        if (mensagem.has("location")) {
            double lat = mensagem.path("location").path("latitude").asDouble();
            double lon = mensagem.path("location").path("longitude").asDouble();
            return ultimoPedido(contato, agora).map(p -> {
                p.informarLocalizacao(lat, lon, agora);
                return "Localização recebida. A Defesa Civil vai ver no mapa do painel.";
            }).orElse("Recebemos sua localização. Se precisar de ajuda, responda 2.");
        }

        String texto = mensagem.path("text").asText("").trim();
        String comando = texto.split("[\\s@]", 2)[0].toLowerCase();
        switch (comando) {
            case "/start" -> {
                inscrever(chatId, nome, agora);
                return BOAS_VINDAS;
            }
            case "/parar" -> {
                inscritos.findByChatId(chatId).ifPresent(InscritoTelegram::desativar);
                return "Pronto, você não vai mais receber alertas. Para voltar, envie /start.";
            }
            case "/situacao", "/status" -> {
                return situacaoAgora();
            }
            case "1" -> {
                inscrever(chatId, nome, agora);
                pedidos.save(new PedidoAjuda(PedidoAjuda.Tipo.ESTOU_BEM, PedidoAjuda.Canal.TELEGRAM, contato, nome, agora));
                return ESTOU_BEM;
            }
            case "2" -> {
                inscrever(chatId, nome, agora);
                pedidos.save(new PedidoAjuda(PedidoAjuda.Tipo.PRECISO_DE_AJUDA, PedidoAjuda.Canal.TELEGRAM, contato, nome, agora));
                log.info("Telegram: pedido de ajuda recebido");
                return PRECISO_DE_AJUDA;
            }
            default -> {
                Matcher m = PESSOAS.matcher(texto);
                if (m.matches()) {
                    int pessoas = Integer.parseInt(m.group(1));
                    return ultimoPedido(contato, agora).map(p -> {
                        p.informarPessoas(pessoas, agora);
                        return "Anotado: " + pessoas + (pessoas == 1 ? " pessoa." : " pessoas.");
                    }).orElse("Se precisar de ajuda, responda 2 primeiro.");
                }
                return AJUDA;
            }
        }
    }

    private Optional<PedidoAjuda> ultimoPedido(String contato, Instant agora) {
        return pedidos.findFirstByCanalAndContatoAndTipoAndCriadoEmAfterOrderByIdDesc(
                PedidoAjuda.Canal.TELEGRAM, contato, PedidoAjuda.Tipo.PRECISO_DE_AJUDA, agora.minus(JANELA_DO_PEDIDO));
    }

    private void inscrever(long chatId, String nome, Instant agora) {
        inscritos.findByChatId(chatId).ifPresentOrElse(
                i -> i.ativar(nome),
                () -> inscritos.save(new InscritoTelegram(chatId, nome, agora)));
    }

    private String situacaoAgora() {
        AvaliacaoDeRisco r = motor.atual();
        String prefixo = r.simulado() ? "[SIMULAÇÃO] " : "";
        if (r.principal() == null) {
            return prefixo + MontadorDeMensagem.simbolo(r.nivel()) + " Normal: nenhum sinal de risco de cheia ou seca agora.";
        }
        return prefixo + MontadorDeMensagem.simbolo(r.nivel()) + " " + MensagensDeRisco.titulo(r.nivel(), r.tipo()) + "\n"
                + MensagensDeRisco.explicar(r.principal());
    }
}
