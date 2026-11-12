package br.com.avisabrasil.previsao;

/** Partes do dia, para falar "amanhã à tarde" em vez de horários. */
public enum Periodo {
    MADRUGADA, MANHA, TARDE, NOITE;

    public static Periodo doHorario(int hora) {
        if (hora < 6) {
            return MADRUGADA;
        }
        if (hora < 12) {
            return MANHA;
        }
        if (hora < 18) {
            return TARDE;
        }
        return NOITE;
    }
}
