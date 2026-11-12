package br.com.avisabrasil.dominio;

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
 * Resposta de uma pessoa da comunidade: "estou bem" ou "preciso de ajuda". Chega pelo Telegram
 * (1 / 2) e, na etapa 7, também pela tela "Preciso de ajuda". O painel da Defesa Civil marca como atendido.
 */
@Entity
@Table(name = "pedido_ajuda")
public class PedidoAjuda {

    public enum Tipo { ESTOU_BEM, PRECISO_DE_AJUDA }

    public enum Canal { TELEGRAM, WEB }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    /** Identificação no canal (no Telegram, o id da conversa). */
    @Column(length = 60)
    private String contato;

    @Column(length = 80)
    private String nome;

    private Integer pessoas;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private boolean atendido;

    @Column(nullable = false)
    private Instant criadoEm;

    private Instant atualizadoEm;

    protected PedidoAjuda() {
    }

    public PedidoAjuda(Tipo tipo, Canal canal, String contato, String nome, Instant criadoEm) {
        this.tipo = tipo;
        this.canal = canal;
        this.contato = contato;
        this.nome = nome;
        this.criadoEm = criadoEm;
    }

    public void informarPessoas(int pessoas, Instant quando) {
        this.pessoas = pessoas;
        this.atualizadoEm = quando;
    }

    public void informarLocalizacao(double latitude, double longitude, Instant quando) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.atualizadoEm = quando;
    }

    public void marcarAtendido(Instant quando) {
        this.atendido = true;
        this.atualizadoEm = quando;
    }

    public Long getId() {
        return id;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Canal getCanal() {
        return canal;
    }

    public String getContato() {
        return contato;
    }

    public String getNome() {
        return nome;
    }

    public Integer getPessoas() {
        return pessoas;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public boolean isAtendido() {
        return atendido;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
