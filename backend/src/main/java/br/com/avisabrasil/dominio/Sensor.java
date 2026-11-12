package br.com.avisabrasil.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Sensor de nível do rio. Nesta versão é simulado (simulador Java ou ESP32 no Wokwi). */
@Entity
@Table(name = "sensor")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean simulado;

    private double latitude;

    private double longitude;

    @Column(length = 300)
    private String descricao;

    protected Sensor() {
    }

    public Sensor(String codigo, String nome, boolean simulado, double latitude, double longitude, String descricao) {
        this.codigo = codigo;
        atualizar(nome, simulado, latitude, longitude, descricao);
    }

    public final void atualizar(String nome, boolean simulado, double latitude, double longitude, String descricao) {
        this.nome = nome;
        this.simulado = simulado;
        this.latitude = latitude;
        this.longitude = longitude;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public boolean isSimulado() {
        return simulado;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getDescricao() {
        return descricao;
    }
}
