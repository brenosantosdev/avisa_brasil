package br.com.avisabrasil.web.painel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Entrada no Painel da Defesa Civil e canal de eventos ao vivo (Server-Sent Events). */
@RestController
@RequestMapping("/api/painel")
@Tag(name = "Painel", description = "Acesso da Defesa Civil (senha em PAINEL_SENHA) e eventos ao vivo")
public class PainelController {

    public record Entrada(String senha) {
    }

    private final AcessoPainel acesso;
    private final EventosDoPainel eventos;

    public PainelController(AcessoPainel acesso, EventosDoPainel eventos) {
        this.acesso = acesso;
        this.eventos = eventos;
    }

    @GetMapping("/sessao")
    @Operation(summary = "Diz se este navegador já entrou no painel e se a senha está configurada no servidor")
    public Map<String, Boolean> sessao(HttpServletRequest pedido) {
        return Map.of("autenticado", ProtecaoDoPainel.autenticado(pedido), "senhaConfigurada", acesso.senhaConfigurada());
    }

    @PostMapping("/entrar")
    @Operation(summary = "Entrar no painel com a senha (PAINEL_SENHA)")
    public ResponseEntity<Map<String, Object>> entrar(@RequestBody Entrada entrada, HttpServletRequest pedido) {
        return switch (acesso.verificar(entrada.senha(), pedido.getRemoteAddr())) {
            case OK -> {
                HttpSession sessao = pedido.getSession(true);
                pedido.changeSessionId(); // evita fixação de sessão
                sessao.setAttribute(AcessoPainel.ATRIBUTO_SESSAO, Boolean.TRUE);
                yield ResponseEntity.ok(Map.of("autenticado", true));
            }
            case SENHA_ERRADA -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", "SENHA_ERRADA"));
            case BLOQUEADO -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("erro", "BLOQUEADO"));
            case PAINEL_TRANCADO -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", "PAINEL_TRANCADO"));
        };
    }

    @PostMapping("/sair")
    @Operation(summary = "Sair do painel")
    public Map<String, Boolean> sair(HttpServletRequest pedido) {
        HttpSession sessao = pedido.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        return Map.of("autenticado", false);
    }

    @GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Eventos ao vivo para o painel (SSE): risco, leitura, alerta e pedido")
    public SseEmitter eventos() {
        return eventos.conectar();
    }
}
