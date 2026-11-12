package br.com.avisabrasil.previsao;

import br.com.avisabrasil.integracoes.Situacao;
import java.time.LocalDate;
import java.util.List;

/** Previsão de vazão do rio, comparada com o normal da época. */
public record PrevisaoRio(
        String rio,
        double latitudeCelula,
        double longitudeCelula,
        LocalDate hoje,
        List<Dia> dias,
        Resumo resumo,
        Historico historico) {

    /**
     * Um dia da série. {@code previsao=false} para dias até hoje.
     * Os campos normal* ficam nulos se o histórico não estiver disponível.
     */
    public record Dia(
            LocalDate data,
            boolean previsao,
            Double vazao,
            Double p25,
            Double p75,
            Double normalP25,
            Double normalMediana,
            Double normalP75) {
    }

    /**
     * @param variacaoPercentual7Dias quanto a vazão muda de hoje para daqui a 7 dias, em %
     * @param mudancaRapida verdadeiro se a mudança em 7 dias passar de 20%
     */
    public record Resumo(
            Double vazaoHoje,
            Long baldesPorSegundoHoje,
            Double vazaoEm7Dias,
            Double variacaoPercentual7Dias,
            Tendencia tendencia,
            boolean mudancaRapida,
            LocalDate dataPico,
            Double vazaoPico,
            ComparacaoNormal comparacaoHoje,
            FaixaNormal normalHoje) {
    }

    /** Situação do histórico e os extremos registrados na série. */
    public record Historico(
            Situacao situacao,
            LocalDate inicio,
            LocalDate dataMaximo,
            Double vazaoMaximo,
            LocalDate dataMinimo,
            Double vazaoMinimo) {
    }
}
