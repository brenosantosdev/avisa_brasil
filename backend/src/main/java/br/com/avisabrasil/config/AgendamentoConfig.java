package br.com.avisabrasil.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas. Nos testes fica desligado para não acessar a internet. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "avisabrasil.agendador.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendamentoConfig {
}
