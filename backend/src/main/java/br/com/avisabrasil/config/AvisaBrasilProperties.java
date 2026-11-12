package br.com.avisabrasil.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações do AvisaBrasil (prefixo "avisabrasil" no application.properties). */
@ConfigurationProperties(prefix = "avisabrasil")
public record AvisaBrasilProperties(Comunidade comunidade, Fontes fontes, Ana ana, Risco risco, Sensor sensor) {

    /**
     * Integração com a ANA (HidroWebService). Usuário e senha vêm só das variáveis de ambiente
     * ANA_USUARIO e ANA_SENHA; sem elas a integração fica desligada.
     *
     * @param validadeToken por quanto tempo reaproveitar o token (a ANA dá 60 min)
     * @param intervaloMinimoLogin nunca tentar login de novo antes disso (evita bloqueio do IP)
     * @param pausaHistorico pausa entre os pedidos que baixam o histórico ano a ano
     * @param principal estação com telemetria, usada para o nível atual e a régua
     * @param cidade régua convencional na própria cidade, usada no histórico
     */
    public record Ana(
            String url,
            Duration intervaloTelemetria,
            Duration validadeToken,
            Duration intervaloMinimoLogin,
            Duration pausaHistorico,
            Estacao principal,
            Estacao cidade,
            Cotas cotas) {
    }

    /**
     * Estação da ANA (código confirmado no inventário HidroInventarioEstacoes).
     *
     * @param inicioHistorico primeiro ano com leituras de régua no inventário
     * @param localizacao como a tela descreve a posição da estação em relação à cidade
     */
    public record Estacao(int codigo, String nome, String rio, int inicioHistorico, String localizacao) {
    }

    /**
     * Cotas de referência em centímetros, na régua da estação principal. Vazias = não há cota
     * oficial; o sistema calcula valores provisórios a partir do histórico da ANA.
     *
     * @param fonte de onde vieram as cotas de cheia configuradas (obrigatório quando preenchidas)
     * @param secaFonte de onde vieram as cotas de seca configuradas (obrigatório quando preenchidas)
     */
    public record Cotas(
            Integer atencaoCm,
            Integer alertaCm,
            Integer emergenciaCm,
            String fonte,
            Integer secaAtencaoCm,
            Integer secaAlertaCm,
            Integer secaEmergenciaCm,
            String secaFonte) {
    }

    /**
     * Regras do motor de risco.
     *
     * @param horasAntecedenciaCheia sobe o nível antes, se a tendência indicar que a próxima cota de
     *     cheia será atingida em até este número de horas
     * @param horasAntecedenciaSeca o mesmo para a seca (o rio desce devagar, então o prazo é maior)
     * @param chuvaForteMm chuva prevista para hoje ou amanhã a partir da qual o nível vai a Atenção
     * @param sensorSemDadosApos sem leitura nova nesse tempo, o sensor deixa de contar
     */
    public record Risco(
            int horasAntecedenciaCheia,
            int horasAntecedenciaSeca,
            double chuvaForteMm,
            Duration sensorSemDadosApos) {
    }

    /**
     * Sensor de nível da comunidade. Nesta versão é simulado (não há hardware): as leituras vêm do
     * simulador ou do firmware no Wokwi e usam a régua da estação principal, para valerem as mesmas cotas.
     */
    public record Sensor(String codigo, String nome, boolean simulado, double latitude, double longitude,
            String descricao) {
    }

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
