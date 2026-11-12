package br.com.avisabrasil.dominio;

import br.com.avisabrasil.risco.TipoRisco;
import br.com.avisabrasil.rio.Nivel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Último nível de risco da comunidade (uma linha só). Fica no banco para que, depois de reiniciar,
 * o motor saiba se o nível subiu (e não repita alertas).
 */
@Entity
@Table(name = "estado_risco")
public class EstadoRisco {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nivel nivel = Nivel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TipoRisco tipo;

    private Instant atualizadoEm;

    public Nivel getNivel() {
        return nivel;
    }

    public TipoRisco getTipo() {
        return tipo;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void mudar(Nivel nivel, TipoRisco tipo, Instant quando) {
        this.nivel = nivel;
        this.tipo = tipo;
        this.atualizadoEm = quando;
    }
}
