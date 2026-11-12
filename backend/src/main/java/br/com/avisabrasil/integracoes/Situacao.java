package br.com.avisabrasil.integracoes;

/** Situação de um dado vindo de fonte externa. */
public enum Situacao {
    /** Dado recente, obtido na última tentativa. */
    ATUALIZADO,
    /** Mostrando o último dado válido: a última tentativa falhou ou o dado passou do prazo. */
    DESATUALIZADO,
    /** Nenhum dado válido obtido até agora. */
    INDISPONIVEL
}
