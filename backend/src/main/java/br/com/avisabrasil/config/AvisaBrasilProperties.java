package br.com.avisabrasil.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações do AvisaBrasil (prefixo "avisabrasil" no application.properties). */
@ConfigurationProperties(prefix = "avisabrasil")
public record AvisaBrasilProperties(Comunidade comunidade, Fontes fontes) {

    /**
     * Comunidade monitorada. O ponto do rio é separado do ponto da cidade porque a grade do
     * GloFAS (~5 km) coloca a coordenada da sede num igarapé, e não no leito do Rio Negro.
     */
    public record Comunidade(
            String nome,
            String uf,
            String rio,
            double latitude,
            double longitude,
            double rioLatitude,
            double rioLongitude) {
    }

    /** Fontes de dados externas e regras de atualização do cache. */
    public record Fontes(
            Fonte openMeteo,
            Fonte glofas,
            Fonte glofasHistorico,
            Overpass overpass,
            Duration novaTentativa,
            Path pastaCache) {
    }

    /** URL da API e de quanto em quanto tempo buscar dados novos. */
    public record Fonte(String url, Duration intervalo) {
    }

    /**
     * @param servidores servidores Overpass, tentados em ordem
     * @param raioMetros raio em volta da sede onde procurar pontos
     */
    public record Overpass(List<String> servidores, Duration intervalo, int raioMetros) {
    }
}
