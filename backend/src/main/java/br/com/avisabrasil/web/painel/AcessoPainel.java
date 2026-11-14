package br.com.avisabrasil.web.painel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Senha do Painel da Defesa Civil (variável PAINEL_SENHA).
 *
 * <ul>
 *   <li>Sem a variável, o painel fica trancado: ninguém entra (padrão seguro).</li>
 *   <li>A comparação é em tempo constante.</li>
 *   <li>Depois de 5 senhas erradas do mesmo endereço, bloqueia esse endereço por 15 minutos.</li>
 * </ul>
 */
@Component
public class AcessoPainel {

    /** Atributo da sessão que marca quem entrou no painel. */
    public static final String ATRIBUTO_SESSAO = "avisabrasil.painel";
    static final int MAXIMO_TENTATIVAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(AcessoPainel.class);

    /** Resultado da tentativa de entrar. */
    public enum Resultado { OK, SENHA_ERRADA, BLOQUEADO, PAINEL_TRANCADO }

    private record Tentativas(int falhas, Instant bloqueadoAte) {
    }

    private final byte[] senha;
    private final Clock relogio;
    private final Map<String, Tentativas> porEndereco = new ConcurrentHashMap<>();

    public AcessoPainel(@Value("${avisabrasil.segredos.painel-senha:}") String senha, Clock relogio) {
        this.senha = senha.getBytes(StandardCharsets.UTF_8);
        this.relogio = relogio;
        if (this.senha.length == 0) {
            log.warn("Painel: PAINEL_SENHA não definida; o painel da Defesa Civil fica trancado");
        }
    }

    public boolean senhaConfigurada() {
        return senha.length > 0;
    }

    public Resultado verificar(String tentativa, String endereco) {
        if (!senhaConfigurada()) {
            return Resultado.PAINEL_TRANCADO;
        }
        Instant agora = relogio.instant();
        Tentativas t = porEndereco.get(endereco);
        if (t != null && t.bloqueadoAte() != null && agora.isBefore(t.bloqueadoAte())) {
            return Resultado.BLOQUEADO;
        }
        boolean certa = tentativa != null && MessageDigest.isEqual(senha, tentativa.getBytes(StandardCharsets.UTF_8));
        if (certa) {
            porEndereco.remove(endereco);
            return Resultado.OK;
        }
        int falhas = (t == null || t.bloqueadoAte() != null ? 0 : t.falhas()) + 1;
        if (falhas >= MAXIMO_TENTATIVAS) {
            porEndereco.put(endereco, new Tentativas(0, agora.plus(BLOQUEIO)));
            log.warn("Painel: endereço bloqueado por {} minutos após {} senhas erradas", BLOQUEIO.toMinutes(), falhas);
            return Resultado.BLOQUEADO;
        }
        porEndereco.put(endereco, new Tentativas(falhas, null));
        return Resultado.SENHA_ERRADA;
    }
}
