package br.com.avisabrasil.notificacao;

import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.risco.AvaliacaoDeRisco;
import br.com.avisabrasil.risco.MensagensDeRisco;
import br.com.avisabrasil.risco.TipoRisco;
import br.com.avisabrasil.rio.Nivel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Monta o texto do alerta no mesmo estilo das telas: nível, o que está acontecendo, o que fazer
 * agora e a fonte. As orientações vêm do mesmo arquivo de tradução das telas (i18n/pt.json).
 */
@Component
public class MontadorDeMensagem {

    static final String MARCA_SIMULACAO = "[SIMULAÇÃO]";
    static final int ACOES_NA_MENSAGEM = 3;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final JsonNode textos;
    private final Clock relogio;

    public MontadorDeMensagem(ObjectMapper mapper, Clock relogio) {
        this.relogio = relogio;
        try (InputStream in = new ClassPathResource("static/i18n/pt.json").getInputStream()) {
            this.textos = mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler i18n/pt.json", e);
        }
    }

    public MensagemAlerta montar(Alerta alerta, AvaliacaoDeRisco avaliacao) {
        Nivel nivel = alerta.getNivel();
        TipoRisco tipo = alerta.getTipo();
        boolean normalizado = alerta.getEvento() == Alerta.Evento.NORMALIZADO;
        boolean manual = alerta.getEvento() == Alerta.Evento.MANUAL;
        String prefixo = alerta.isSimulado() ? MARCA_SIMULACAO + " " : "";

        String titulo = prefixo + simbolo(nivel) + " AvisaBrasil: " + MensagensDeRisco.titulo(nivel, tipo);
        String acontecendo = normalizado
                ? "O rio voltou ao nível normal. Continue acompanhando os avisos."
                : manual ? alerta.getMensagem()
                : (avaliacao != null && avaliacao.principal() != null
                        ? MensagensDeRisco.explicar(avaliacao.principal())
                        : alerta.getMensagem());
        List<String> acoes = acoes(normalizado || nivel == Nivel.NORMAL ? "acoes.NORMAL" : "acoes." + tipo + "." + nivel);
        String fonte = "Fonte: " + fonte(alerta) + ". AvisaBrasil, " + ZonedDateTime.ofInstant(alerta.getCriadoEm(),
                relogio.getZone()).format(HORA) + ".";

        StringBuilder texto = new StringBuilder();
        if (alerta.isSimulado()) {
            texto.append(MARCA_SIMULACAO).append(" Isto é uma demonstração, não é medição real.\n\n");
        }
        texto.append(simbolo(nivel)).append(' ').append(MensagensDeRisco.titulo(nivel, tipo)).append("\n\n");
        texto.append("O que está acontecendo: ").append(acontecendo).append("\n\n");
        texto.append("O que fazer agora:\n");
        acoes.forEach(a -> texto.append("• ").append(a).append('\n'));
        texto.append('\n').append(fonte);

        String curto = prefixo + "AvisaBrasil: " + MensagensDeRisco.titulo(nivel, tipo) + ". " + acontecendo
                + (acoes.isEmpty() ? "" : " Faça: " + acoes.get(0)) + " " + fonte;

        // no alerta manual, o texto faz parte do conteúdo: textos diferentes não contam como repetição
        String assinatura = alerta.getEvento() + "|" + nivel + "|" + tipo + "|" + alerta.isSimulado()
                + (manual ? "|" + Integer.toHexString(alerta.getMensagem().hashCode()) : "");
        return new MensagemAlerta(titulo, texto.toString(), curto, nivel, tipo, alerta.isSimulado(), false, assinatura);
    }

    /** Mensagem de teste, para conferir se o celular recebe (não é alerta). */
    public MensagemAlerta teste() {
        String texto = "[TESTE] Mensagem de teste do AvisaBrasil. Se você recebeu, este canal está funcionando. "
                + "Não é um alerta.";
        return new MensagemAlerta("[TESTE] AvisaBrasil", texto, texto, Nivel.NORMAL, null, false, true, "TESTE");
    }

    private List<String> acoes(String chave) {
        List<String> lista = new ArrayList<>();
        textos.path(chave).forEach(n -> lista.add(n.asText()));
        return lista.subList(0, Math.min(ACOES_NA_MENSAGEM, lista.size()));
    }

    private static String fonte(Alerta alerta) {
        if (alerta.getEvento() == Alerta.Evento.MANUAL) {
            return "Defesa Civil (alerta manual)";
        }
        if (alerta.getFonte() == null) {
            return "régua da ANA e previsões";
        }
        return switch (alerta.getFonte()) {
            case ANA -> "medição da ANA (régua do Rio Negro)";
            case SENSOR -> alerta.isSimulado() ? "sensor SIMULADO" : "sensor da comunidade";
            case CHUVA -> "previsão do tempo (Open-Meteo)";
            case GLOFAS -> "modelo GloFAS";
        };
    }

    /** Círculo colorido + palavra no título: a cor nunca aparece sozinha. */
    static String simbolo(Nivel nivel) {
        return switch (nivel) {
            case NORMAL -> "🟢";
            case ATENCAO -> "🟡";
            case ALERTA -> "🟠";
            case EMERGENCIA -> "🔴";
        };
    }
}
