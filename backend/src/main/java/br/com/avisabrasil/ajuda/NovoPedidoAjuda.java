package br.com.avisabrasil.ajuda;

import br.com.avisabrasil.dominio.PedidoAjuda;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Pedido enviado pela tela "Preciso de ajuda".
 *
 * @param idCliente identificador gerado no celular: a fila offline pode reenviar, e o servidor não duplica
 * @param criadoNoCliente quando a pessoa apertou o botão (pode ser antes do envio, se estava sem internet)
 */
public record NovoPedidoAjuda(
        @NotNull PedidoAjuda.Tipo tipo,
        @Min(1) @Max(200) Integer pessoas,
        @Size(max = 80) String nome,
        @Size(max = 30) @Pattern(regexp = "^[0-9 +()-]*$") String telefone,
        @Size(max = 300) String mensagem,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Size(min = 8, max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$") String idCliente,
        OffsetDateTime criadoNoCliente) {
}
