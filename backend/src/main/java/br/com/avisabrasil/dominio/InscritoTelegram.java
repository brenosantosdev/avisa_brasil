package br.com.avisabrasil.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Conversa do Telegram (pessoa ou grupo) que pediu para receber os alertas com /start. */
@Entity
@Table(name = "inscrito_telegram")
public class InscritoTelegram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long chatId;

    @Column(length = 80)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private Instant inscritoEm;

    protected InscritoTelegram() {
    }

    public InscritoTelegram(Long chatId, String nome, Instant inscritoEm) {
        this.chatId = chatId;
        this.nome = nome;
        this.inscritoEm = inscritoEm;
    }

    public void ativar(String nome) {
        this.ativo = true;
        this.nome = nome;
    }

    public void desativar() {
        this.ativo = false;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
