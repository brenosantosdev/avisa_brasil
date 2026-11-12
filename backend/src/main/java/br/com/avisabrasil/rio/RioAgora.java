package br.com.avisabrasil.rio;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.previsao.ComparacaoNormal;
import br.com.avisabrasil.previsao.FaixaNormal;
import br.com.avisabrasil.risco.AvaliacaoMedicao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Situação do rio medida pela ANA. Campos ficam nulos quando o dado ainda não existe
 * (por exemplo, histórico ainda baixando).
 *
 * @param velocidadeCmPorDia velocidade de subida (positiva) ou descida (negativa), por regressão
 *     linear das leituras das últimas 24 horas
 * @param avaliacao níveis de cheia e de seca desta medição, pelas regras do motor de risco
 * @param comparacaoAna nível de hoje comparado com a mesma época nos anos anteriores (régua da ANA)
 * @param comparacaoGlofas o que o modelo GloFAS diz para hoje
 * @param divergem ANA e GloFAS discordam sobre seca/cheia; nesse caso vale a ANA
 */
public record RioAgora(
        AvisaBrasilProperties.Estacao estacao,
        Leitura atual,
        Double variacao24hCm,
        Double velocidadeCmPorDia,
        List<Ponto> ultimosDias,
        CotasDeReferencia cotas,
        AvaliacaoMedicao avaliacao,
        FaixaNormal normalHoje,
        ComparacaoNormal comparacaoAna,
        ComparacaoNormal comparacaoGlofas,
        boolean divergem,
        Historico historicoPrincipal,
        Historico historicoCidade) {

    public record Leitura(LocalDateTime dataHora, double cotaCm, Double vazaoM3s) {
    }

    public record Ponto(LocalDateTime dataHora, double cotaCm) {
    }

    /**
     * @param ultimaLeitura dia mais recente com leitura manual publicada pela ANA
     */
    public record Historico(
            AvisaBrasilProperties.Estacao estacao,
            HistoricoAnaServico.Progresso progresso,
            int anosCompletos,
            LocalDate primeiraLeitura,
            LocalDate ultimaLeitura,
            List<ExtremosAnuais.Evento> maioresCheias,
            List<ExtremosAnuais.Evento> maioresSecas) {
    }
}
