package br.com.avisabrasil.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dados que aparecem no topo do Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI avisaBrasilOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AvisaBrasil API")
                .version("0.0.1")
                .description("Rede de alerta de cheias e secas para comunidades ribeirinhas e indígenas. "
                        + "Território piloto: São Gabriel da Cachoeira (AM), Rio Negro.")
                .contact(new Contact().name("Breno Santos (FIAP)")));
    }
}
