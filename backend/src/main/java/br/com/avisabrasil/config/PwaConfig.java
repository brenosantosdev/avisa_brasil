package br.com.avisabrasil.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Versão para celular (PWA): o manifesto precisa do tipo application/manifest+json para o navegador oferecer
 * a instalação, e o service worker (sw.js) não pode ficar preso no cache do navegador, para as atualizações
 * chegarem ao celular.
 */
@Configuration
public class PwaConfig implements WebMvcConfigurer {

    static final MediaType MANIFESTO = MediaType.valueOf("application/manifest+json");

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurador) {
        configurador.mediaType("webmanifest", MANIFESTO);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/sw.js")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }

    /** O mesmo tipo no servidor embutido (Tomcat), que responde antes do Spring em alguns casos. */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> tipoDoManifesto() {
        return fabrica -> {
            MimeMappings tipos = new MimeMappings(MimeMappings.DEFAULT);
            tipos.add("webmanifest", MANIFESTO.toString());
            fabrica.setMimeMappings(tipos);
        };
    }
}
