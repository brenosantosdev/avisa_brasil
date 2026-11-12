package br.com.avisabrasil.mapa;

import java.util.List;

/** Contas geográficas simples, suficientes para distâncias de poucos quilômetros. */
public final class Geo {

    private static final double RAIO_TERRA_METROS = 6_371_000;

    private Geo() {
    }

    /** Distância em linha reta (fórmula de haversine), em metros. */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RAIO_TERRA_METROS * Math.asin(Math.sqrt(a));
    }

    /**
     * Diz se o ponto está dentro do anel (lista de [lon, lat], como no GeoJSON),
     * pelo método do raio (conta quantas bordas uma linha horizontal cruza).
     */
    public static boolean dentroDoAnel(double lat, double lon, List<double[]> anel) {
        boolean dentro = false;
        for (int i = 0, j = anel.size() - 1; i < anel.size(); j = i++) {
            double xi = anel.get(i)[0];
            double yi = anel.get(i)[1];
            double xj = anel.get(j)[0];
            double yj = anel.get(j)[1];
            boolean cruza = (yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi;
            if (cruza) {
                dentro = !dentro;
            }
        }
        return dentro;
    }
}
