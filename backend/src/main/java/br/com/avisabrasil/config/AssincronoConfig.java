package br.com.avisabrasil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Envio de alertas fora da requisição, para o sensor e o painel não esperarem o Telegram ou o SMS. */
@Configuration
@EnableAsync
public class AssincronoConfig {
}
