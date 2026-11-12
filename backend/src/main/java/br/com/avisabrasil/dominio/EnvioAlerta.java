package br.com.avisabrasil.dominio;

import br.com.avisabrasil.notificacao.Canal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Resultado do envio de um alerta por um canal. Um alerta nunca é enviado duas vezes pelo mesmo canal. */
@Entity
@Table(name = "envio_alerta", uniqueConstraints = @UniqueConstraint(columnNames = {"alertaId", "canal"}))
public class EnvioAlerta {

    public enum Status {
        ENVIADO,
        FALHOU,
        /** Mesmo conteúdo do último alerta enviado por este canal: não reenviado. */
        IGNORADO_REPETIDO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long alertaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, length = 80)
    private String assinatura;

    /** Descrição segura da falha (nunca contém tokens). */
    @Column(length = 300)
    private String erro;

    @Column(nullable = false)
    private Instant registradoEm;

    protected EnvioAlerta() {
    }

    public EnvioAlerta(Long alertaId, Canal canal, Status status, String assinatura, String erro, Instant registradoEm) {
        this.alertaId = alertaId;
        this.canal = canal;
        this.status = status;
        this.assinatura = assinatura;
        this.erro = erro;
        this.registradoEm = registradoEm;
    }

    public Long getAlertaId() {
        return alertaId;
    }

    public Canal getCanal() {
        return canal;
    }

    public Status getStatus() {
        return status;
    }

    public String getAssinatura() {
        return assinatura;
    }

    public String getErro() {
        return erro;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
