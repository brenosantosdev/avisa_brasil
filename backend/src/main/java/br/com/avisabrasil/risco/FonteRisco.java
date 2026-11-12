package br.com.avisabrasil.risco;

/** De onde veio cada fator. Em empate de nível, vale a primeira da lista (medição oficial primeiro). */
public enum FonteRisco {
    /** Régua da ANA (medição oficial). */
    ANA,
    /** Sensor da comunidade (nesta versão, simulado). */
    SENSOR,
    /** Chuva prevista (Open-Meteo). */
    CHUVA,
    /** Vazão prevista pelo modelo GloFAS. */
    GLOFAS
}
