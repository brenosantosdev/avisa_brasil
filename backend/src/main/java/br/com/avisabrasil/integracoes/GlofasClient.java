package br.com.avisabrasil.integracoes;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Busca a vazão do rio no GloFAS (Copernicus) pela API de cheias do Open-Meteo. */
@Component
public class GlofasClient {

    public static final String FONTE = "GloFAS (Copernicus) via Open-Meteo";
    public static final String FONTE_URL = "https://open-meteo.com/en/docs/flood-api";
    /** Antes de 1997 a série dessa célula vem vazia. */
    public static final LocalDate INICIO_HISTORICO = LocalDate.of(1997, 1, 1);

    private final RestClient http;
    private final AvisaBrasilProperties propriedades;

    public GlofasClient(RestClient http, AvisaBrasilProperties propriedades) {
        this.http = http;
        this.propriedades = propriedades;
    }

    /** Últimos 30 dias e previsão de 30 dias, com mediana e faixa p25–p75 do conjunto de previsões. */
    public String buscarPrevisao() {
        URI uri = base(propriedades.fontes().glofas().url())
                .queryParam("daily", "river_discharge,river_discharge_median,"
                        + "river_discharge_p25,river_discharge_p75")
                .queryParam("past_days", 30)
                .queryParam("forecast_days", 30)
                .build().encode().toUri();
        return http.get().uri(uri).retrieve().body(String.class);
    }

    /** Série diária desde 1997 até {@code ate}, usada para saber o que é "normal" em cada época do ano. */
    public String buscarHistorico(LocalDate ate) {
        URI uri = base(propriedades.fontes().glofasHistorico().url())
                .queryParam("daily", "river_discharge")
                .queryParam("start_date", INICIO_HISTORICO)
                .queryParam("end_date", ate)
                .build().encode().toUri();
        return http.get().uri(uri).retrieve().body(String.class);
    }

    private UriComponentsBuilder base(String url) {
        var comunidade = propriedades.comunidade();
        return UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", comunidade.rioLatitude())
                .queryParam("longitude", comunidade.rioLongitude());
    }
}
