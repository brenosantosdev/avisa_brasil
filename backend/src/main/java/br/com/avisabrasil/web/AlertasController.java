package br.com.avisabrasil.web;

import br.com.avisabrasil.dominio.Alerta;
import br.com.avisabrasil.dominio.EnvioAlerta;
import br.com.avisabrasil.dominio.PedidoAjuda;
import br.com.avisabrasil.notificacao.ServicoDeAlertas;
import br.com.avisabrasil.repositorio.EnvioAlertaRepositorio;
import br.com.avisabrasil.repositorio.PedidoAjudaRepositorio;
import br.com.avisabrasil.risco.MotorDeRisco;
import br.com.avisabrasil.risco.SimulacaoServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Canais de alerta, histórico de envios, respostas da comunidade e limpeza da demonstração. */
@RestController
@RequestMapping("/api")
@Tag(name = "Alertas", description = "Envio de alertas (log, ntfy, Telegram, SMS) e respostas da comunidade")
public class AlertasController {

    /** Alerta com o resultado do envio por canal. */
    public record AlertaComEnvios(Alerta alerta, List<EnvioAlerta> envios) {
    }

    private final ServicoDeAlertas servico;
    private final MotorDeRisco motor;
    private final EnvioAlertaRepositorio envios;
    private final PedidoAjudaRepositorio pedidos;
    private final SimulacaoServico simulacao;

    public AlertasController(ServicoDeAlertas servico, MotorDeRisco motor, EnvioAlertaRepositorio envios,
            PedidoAjudaRepositorio pedidos, SimulacaoServico simulacao) {
        this.servico = servico;
        this.motor = motor;
        this.envios = envios;
        this.pedidos = pedidos;
        this.simulacao = simulacao;
    }

    @GetMapping("/alertas/canais")
    @Operation(summary = "Quais canais estão ligados (cada um se desliga sozinho sem a sua variável de ambiente)")
    public List<ServicoDeAlertas.SituacaoCanal> canais() {
        return servico.canais();
    }

    @PostMapping("/alertas/teste")
    @Operation(summary = "Envia uma mensagem de TESTE por todos os canais ligados (não é alerta)")
    public List<ServicoDeAlertas.ResultadoTeste> teste() {
        return servico.enviarTeste();
    }

    @GetMapping("/alertas/envios")
    @Operation(summary = "Últimos 30 alertas com o resultado do envio por canal")
    public List<AlertaComEnvios> alertasComEnvios() {
        List<Alerta> alertas = motor.ultimosAlertas(30);
        Map<Long, List<EnvioAlerta>> porAlerta = envios.findByAlertaIdIn(alertas.stream().map(Alerta::getId).toList())
                .stream().collect(Collectors.groupingBy(EnvioAlerta::getAlertaId));
        return alertas.stream().map(a -> new AlertaComEnvios(a, porAlerta.getOrDefault(a.getId(), List.of()))).toList();
    }

    @GetMapping("/pedidos-ajuda")
    @Operation(summary = "Respostas da comunidade: \"estou bem\" e \"preciso de ajuda\" (as 100 mais recentes)")
    public List<PedidoAjuda> pedidos() {
        return pedidos.findTop100ByOrderByIdDesc();
    }

    @PostMapping("/simulacao/limpar")
    @Operation(summary = "Apaga leituras e alertas SIMULADOS (antes da demonstração)",
            description = "Dados reais ficam. O estado do risco volta ao real sem enviar alerta.")
    public SimulacaoServico.Limpeza limparSimulacao() {
        return simulacao.limpar();
    }
}
