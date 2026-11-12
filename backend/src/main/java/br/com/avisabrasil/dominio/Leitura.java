package br.com.avisabrasil.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Leitura de nível de um sensor.
 *
 * @see #dataHora momento da medição informado pelo sensor (no simulador, é o tempo simulado)
 * @see #recebidoEm quando o servidor recebeu (usado para saber se o sensor está ativo)
 */
@Entity
@Table(name = "leitura", indexes = @Index(name = "ix_leitura_sensor", columnList = "sensor_id, id"))
public class Leitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @Column(nullable = false)
    private Instant dataHora;

    @Column(nullable = false)
    private double nivelCm;

    @Column(nullable = false)
    private Instant recebidoEm;

    protected Leitura() {
    }

    public Leitura(Sensor sensor, Instant dataHora, double nivelCm, Instant recebidoEm) {
        this.sensor = sensor;
        this.dataHora = dataHora;
        this.nivelCm = nivelCm;
        this.recebidoEm = recebidoEm;
    }

    public Long getId() {
        return id;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public double getNivelCm() {
        return nivelCm;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }
}
