package br.com.avisabrasil.mapa;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados para a Defesa Civil cadastrar um ponto seguro. */
public record NovoPontoSeguro(
        @NotBlank @Size(max = 120) String nome,
        @NotNull TipoPonto tipo,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 500) String observacao,
        @Min(0) @Max(100_000) Integer capacidade) {
}
