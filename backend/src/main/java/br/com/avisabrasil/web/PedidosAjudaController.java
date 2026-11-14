package br.com.avisabrasil.web;

import br.com.avisabrasil.ajuda.NovoPedidoAjuda;
import br.com.avisabrasil.ajuda.PedidosAjudaServico;
import br.com.avisabrasil.dominio.PedidoAjuda;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** "Preciso de ajuda" e "Estou bem": enviar é público; ver a lista e marcar como atendido é da Defesa Civil. */
@RestController
@RequestMapping("/api/pedidos-ajuda")
@Tag(name = "Pedidos de ajuda", description = "Respostas da comunidade (tela web e Telegram)")
public class PedidosAjudaController {

    private final PedidosAjudaServico servico;

    public PedidosAjudaController(PedidosAjudaServico servico) {
        this.servico = servico;
    }

    @PostMapping
    @Operation(summary = "Enviar \"preciso de ajuda\" ou \"estou bem\" (público)",
            description = "Reenviar o mesmo idCliente (fila offline do celular) não duplica: devolve o pedido já gravado.")
    public ResponseEntity<Map<String, Object>> enviar(@Valid @RequestBody NovoPedidoAjuda novo, HttpServletRequest pedido) {
        var registro = servico.registrarPelaWeb(novo, pedido.getRemoteAddr());
        // para quem pediu, só a confirmação (a lista com nomes e locais é só do painel)
        return ResponseEntity.status(registro.jaExistia() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(Map.of("id", registro.pedido().getId(), "recebidoEm", registro.pedido().getCriadoEm().toString()));
    }

    @GetMapping
    @Operation(summary = "Últimos 100 pedidos e respostas (painel da Defesa Civil)")
    public List<PedidoAjuda> listar() {
        return servico.ultimos();
    }

    @PatchMapping("/{id}/atendido")
    @Operation(summary = "Marcar pedido como atendido (painel da Defesa Civil)")
    public PedidoAjuda atendido(@PathVariable long id) {
        return servico.marcarAtendido(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
    }

    @ExceptionHandler(PedidosAjudaServico.LimiteExcedidoException.class)
    public ResponseEntity<Map<String, String>> limite(PedidosAjudaServico.LimiteExcedidoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("erro", e.getMessage()));
    }
}
