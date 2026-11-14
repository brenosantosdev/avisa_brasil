package br.com.avisabrasil.risco;

import br.com.avisabrasil.ajuda.PedidosAjudaServico;
import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.dominio.Sensor;
import br.com.avisabrasil.repositorio.AlertaRepositorio;
import br.com.avisabrasil.repositorio.EnvioAlertaRepositorio;
import br.com.avisabrasil.repositorio.LeituraRepositorio;
import br.com.avisabrasil.repositorio.SensorRepositorio;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpa os dados de demonstração antes da apresentação: leituras dos sensores simulados e alertas
 * simulados (com os registros de envio) e, se pedido, os pedidos de ajuda de teste. Dados reais (ANA,
 * alertas reais) ficam.
 */
@Service
public class SimulacaoServico {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoServico.class);

    public record Limpeza(long leituras, long alertas, long envios, long pedidos, AvaliacaoDeRisco situacaoAtual) {
    }

    private final SensorRepositorio sensores;
    private final LeituraRepositorio leituras;
    private final AlertaRepositorio alertas;
    private final EnvioAlertaRepositorio envios;
    private final MotorDeRisco motor;
    private final PedidosAjudaServico pedidos;

    public SimulacaoServico(SensorRepositorio sensores, LeituraRepositorio leituras, AlertaRepositorio alertas,
            EnvioAlertaRepositorio envios, MotorDeRisco motor, PedidosAjudaServico pedidos) {
        this.sensores = sensores;
        this.leituras = leituras;
        this.alertas = alertas;
        this.envios = envios;
        this.motor = motor;
        this.pedidos = pedidos;
    }

    /**
     * @param incluirPedidos também apaga todos os pedidos de ajuda e respostas (testes antes da apresentação)
     */
    @Transactional
    public Limpeza limpar(boolean incluirPedidos) {
        long totalLeituras = 0;
        for (Sensor s : sensores.findAll()) {
            if (s.isSimulado()) {
                totalLeituras += leituras.deleteBySensor(s);
            }
        }
        List<Long> simulados = alertas.findBySimuladoTrue().stream().map(Alerta::getId).toList();
        long totalEnvios = simulados.isEmpty() ? 0 : envios.deleteByAlertaIdIn(simulados);
        if (!simulados.isEmpty()) {
            alertas.deleteAllByIdInBatch(simulados);
        }
        long totalPedidos = incluirPedidos ? pedidos.apagarTodos() : 0;
        // volta o estado para a situação real sem mandar alerta por causa da limpeza
        AvaliacaoDeRisco agora = motor.sincronizarSemAlerta();
        log.info("Simulação: limpos {} leituras, {} alertas e {} envios simulados e {} pedidos; situação atual {}",
                totalLeituras, simulados.size(), totalEnvios, totalPedidos, agora.nivel());
        return new Limpeza(totalLeituras, simulados.size(), totalEnvios, totalPedidos, agora);
    }
}
