package br.com.avisabrasil.web.painel;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exige a sessão do painel nas operações da Defesa Civil: cadastro de pontos seguros, alertas
 * (canais, teste, histórico de envios, alerta manual), limpeza da simulação, pedidos de ajuda
 * (lista e "atendido") e o canal de eventos ao vivo. O envio de um pedido de ajuda é público.
 */
@Configuration
public class ProtecaoDoPainel implements WebMvcConfigurer, HandlerInterceptor {

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(this).addPathPatterns(
                "/api/pontos-seguros/**", "/api/alertas/**", "/api/simulacao/**", "/api/pedidos-ajuda/**", "/api/painel/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest pedido, HttpServletResponse resposta, Object handler) throws IOException {
        if (!protegido(pedido.getMethod(), caminho(pedido)) || autenticado(pedido)) {
            return true;
        }
        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Entre no painel da Defesa Civil.\"}");
        return false;
    }

    /** Regras de quais chamadas exigem a senha do painel. */
    static boolean protegido(String metodo, String caminho) {
        if (caminho.startsWith("/api/painel/")) {
            // entrar, sair e conferir a sessão são livres; o resto (eventos ao vivo) exige senha
            return !caminho.equals("/api/painel/entrar") && !caminho.equals("/api/painel/sair")
                    && !caminho.equals("/api/painel/sessao");
        }
        if (caminho.startsWith("/api/pontos-seguros")) {
            return !metodo.equals("GET"); // a lista aparece no mapa público; cadastrar e remover, não
        }
        if (caminho.equals("/api/pedidos-ajuda")) {
            return !metodo.equals("POST"); // qualquer pessoa pede ajuda; só a Defesa Civil vê a lista
        }
        return true; // /api/alertas/**, /api/simulacao/**, /api/pedidos-ajuda/{id}/...
    }

    static boolean autenticado(HttpServletRequest pedido) {
        HttpSession sessao = pedido.getSession(false);
        return sessao != null && Boolean.TRUE.equals(sessao.getAttribute(AcessoPainel.ATRIBUTO_SESSAO));
    }

    private static String caminho(HttpServletRequest pedido) {
        return pedido.getRequestURI().substring(pedido.getContextPath().length());
    }
}
