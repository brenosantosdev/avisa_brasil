package br.com.avisabrasil.dominio;

import br.com.avisabrasil.risco.FonteRisco;
import br.com.avisabrasil.risco.TipoRisco;
import br.com.avisabrasil.rio.Nivel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Alerta gerado pelo motor de risco: só quando o nível sobe, ou quando volta ao Normal
 * ("situação normalizada"). O envio (ntfy, Telegram, SMS) vem na etapa 6.
 */
@Entity
@Table(name = "alerta")
public class Alerta {

    public enum Evento { NIVEL_SUBIU, NORMALIZADO, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nivel nivel;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TipoRisco tipo;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private FonteRisco fonte;

    @Column(nullable = false)
    private boolean simulado;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Column(nullable = false)
    private Instant criadoEm;

    protected Alerta() {
    }

    public Alerta(Evento evento, Nivel nivel, TipoRisco tipo, FonteRisco fonte, boolean simulado, String mensagem,
            Instant criadoEm) {
        this.evento = evento;
        this.nivel = nivel;
        this.tipo = tipo;
        this.fonte = fonte;
        this.simulado = simulado;
        this.mensagem = mensagem;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public Nivel getNivel() {
        return nivel;
    }

    public TipoRisco getTipo() {
        return tipo;
    }

    public FonteRisco getFonte() {
        return fonte;
    }

    public boolean isSimulado() {
        return simulado;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
