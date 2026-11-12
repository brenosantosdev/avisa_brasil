package br.com.avisabrasil.risco;

import br.com.avisabrasil.rio.Nivel;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Textos dos alertas em português simples (usados no histórico e, na etapa 6, nas notificações).
 * A tela usa as traduções de i18n; estes textos são para as mensagens enviadas.
 */
public final class MensagensDeRisco {

    private static final Locale PT = Locale.forLanguageTag("pt-BR");

    private MensagensDeRisco() {
    }

    public static String titulo(Nivel nivel, TipoRisco tipo) {
        if (nivel == Nivel.NORMAL) {
            return "Situação normalizada";
        }
        String palavra = switch (nivel) {
            case ATENCAO -> "ATENÇÃO";
            case ALERTA -> "ALERTA";
            case EMERGENCIA -> "EMERGÊNCIA";
            case NORMAL -> "";
        };
        return palavra + (tipo == TipoRisco.SECA ? " – SECA" : " – CHEIA");
    }

    /** Mensagem completa: título, motivo e marcação de simulação. */
    public static String mensagem(AvaliacaoDeRisco avaliacao) {
        if (avaliacao.nivel() == Nivel.NORMAL || avaliacao.principal() == null) {
            return "Situação normalizada: o rio voltou ao nível normal.";
        }
        String texto = titulo(avaliacao.nivel(), avaliacao.tipo()) + ". " + explicar(avaliacao.principal());
        return avaliacao.simulado() ? "[SIMULAÇÃO] " + texto : texto;
    }

    public static String explicar(Fator f) {
        Map<String, Object> d = f.dados();
        String nivel = nomeDoNivel(f.nivel());
        String origem = switch (f.fonte()) {
            case ANA -> " (medição da ANA)";
            case SENSOR -> f.simulado() ? " (sensor simulado)" : " (sensor da comunidade)";
            case CHUVA -> " (previsão Open-Meteo)";
            case GLOFAS -> " (modelo GloFAS)";
        };
        String texto = switch (f.motivo()) {
            case COTA_CHEIA -> "O rio está em " + m(d.get("cotaCm")) + ", acima da cota de " + nivel
                    + " de cheia (" + m(d.get("limiteCm")) + ").";
            case COTA_SECA -> "O rio está em " + m(d.get("cotaCm")) + ", abaixo da cota de " + nivel
                    + " de seca (" + m(d.get("limiteCm")) + ").";
            case SECA_EPOCA -> "O rio está em " + m(d.get("cotaCm")) + ": entre os " + d.get("percentual")
                    + "% níveis mais baixos para esta época do ano (" + d.get("anoInicial") + "–" + d.get("anoFinal") + ").";
            case PREVENTIVO_CHEIA -> "O rio está subindo e deve chegar à cota de " + nivel + " ("
                    + m(d.get("limiteCm")) + ") em cerca de " + numero(d.get("horas")) + " horas.";
            case PREVENTIVO_SECA -> "O rio está descendo e deve chegar à cota de " + nivel + " de seca ("
                    + m(d.get("limiteCm")) + ") em cerca de " + numero(d.get("horas")) + " horas.";
            case CHUVA_FORTE -> "Chuva forte prevista: " + numero(d.get("mm")) + " mm em " + d.get("data")
                    + ". Risco de alagamento e deslizamento.";
            case SUBIDA_RAPIDA_PREVISTA -> "O modelo prevê subida rápida do rio (" + numero(d.get("variacao"))
                    + "% em 7 dias).";
        };
        return texto + origem;
    }

    public static String nomeDoNivel(Nivel nivel) {
        return switch (nivel) {
            case NORMAL -> "Normal";
            case ATENCAO -> "Atenção";
            case ALERTA -> "Alerta";
            case EMERGENCIA -> "Emergência";
        };
    }

    private static String m(Object cm) {
        NumberFormat f = NumberFormat.getNumberInstance(PT);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(((Number) cm).doubleValue() / 100) + " m";
    }

    private static String numero(Object valor) {
        NumberFormat f = NumberFormat.getNumberInstance(PT);
        f.setMaximumFractionDigits(1);
        return f.format(((Number) valor).doubleValue());
    }
}
