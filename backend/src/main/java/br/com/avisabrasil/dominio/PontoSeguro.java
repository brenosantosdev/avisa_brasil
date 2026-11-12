package br.com.avisabrasil.dominio;

import br.com.avisabrasil.mapa.TipoPonto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ponto seguro cadastrado pela Defesa Civil (abrigo, escola usada como abrigo, ponto de encontro). */
@Entity
@Table(name = "ponto_seguro")
public class PontoSeguro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPonto tipo;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 500)
    private String observacao;

    /** Quantas pessoas cabem (opcional). */
    private Integer capacidade;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private Instant criadoEm;

    /** Quando foi desativado (removido do mapa). O registro fica guardado para histórico. */
    private Instant removidoEm;

    protected PontoSeguro() {
    }

    public PontoSeguro(String nome, TipoPonto tipo, double latitude, double longitude,
            String observacao, Integer capacidade, Instant criadoEm) {
        this.nome = nome;
        this.tipo = tipo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.observacao = observacao;
        this.capacidade = capacidade;
        this.criadoEm = criadoEm;
    }

    public void desativar(Instant quando) {
        this.ativo = false;
        this.removidoEm = quando;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoPonto getTipo() {
        return tipo;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getObservacao() {
        return observacao;
    }

    public Integer getCapacidade() {
        return capacidade;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getRemovidoEm() {
        return removidoEm;
    }
}
