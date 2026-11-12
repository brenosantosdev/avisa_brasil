package br.com.avisabrasil.rio;

import java.util.Optional;

/** Níveis de risco do AvisaBrasil, do menor para o maior. */
public enum Nivel {
    NORMAL, ATENCAO, ALERTA, EMERGENCIA;

    public boolean maiorQue(Nivel outro) {
        return compareTo(outro) > 0;
    }

    /** Próximo nível acima, ou vazio se já está em Emergência. */
    public Optional<Nivel> proximo() {
        return this == EMERGENCIA ? Optional.empty() : Optional.of(values()[ordinal() + 1]);
    }
}
