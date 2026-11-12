package br.com.avisabrasil.previsao;

/** Aparência do céu, simplificada a partir do código WMO que o Open-Meteo envia. */
public enum Ceu {
    LIMPO, NUBLADO, NEBLINA, CHUVA, TROVOADA;

    public static Ceu doCodigoWmo(Integer codigo) {
        if (codigo == null) {
            return null;
        }
        if (codigo <= 1) {
            return LIMPO;
        }
        if (codigo <= 3) {
            return NUBLADO;
        }
        if (codigo == 45 || codigo == 48) {
            return NEBLINA;
        }
        if (codigo >= 95) {
            return TROVOADA;
        }
        if (codigo >= 51) {
            return CHUVA;
        }
        return NUBLADO;
    }
}
