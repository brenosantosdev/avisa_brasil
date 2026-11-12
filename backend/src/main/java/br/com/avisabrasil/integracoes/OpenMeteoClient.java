package br.com.avisabrasil.integracoes;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Busca a previsão do tempo no Open-Meteo (grátis, sem chave). */
@Component
public class OpenMeteoClient {

    public static final String FONTE = "Open-Meteo";
    public static final String FONTE_URL = "https://open-meteo.com/";

    private final RestClient http;
    private final AvisaBrasilProperties propriedades;

    public OpenMeteoClient(RestClient http, AvisaBrasilProperties propriedades) {
        this.http = http;
        this.propriedades = propriedades;
    }

    /** Retorna o JSON bruto da previsão de 7 dias (por dia e por hora) no fuso de Manaus. */
    public String buscarPrevisao() {
        var comunidade = propriedades.comunidade();
        URI uri = UriComponentsBuilder.fromUriString(propriedades.fontes().openMeteo().url())
                .queryParam("latitude", comunidade.latitude())
                .queryParam("longitude", comunidade.longitude())
                .queryParam("timezone", "America/Manaus")
                .queryParam("forecast_days", 7)
                .queryParam("daily", "precipitation_sum,precipitation_probability_max,"
                        + "temperature_2m_max,temperature_2m_min,weather_code")
                .queryParam("hourly", "precipitation,precipitation_probability,temperature_2m")
                .build().encode().toUri();
        return http.get().uri(uri).retrieve().body(String.class);
    }
}
