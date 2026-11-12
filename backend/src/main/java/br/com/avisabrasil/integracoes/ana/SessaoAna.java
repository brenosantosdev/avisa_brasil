package br.com.avisabrasil.integracoes.ana;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda o token da ANA e controla os logins, para nunca bloquear o IP:
 *
 * <ul>
 *   <li>reaproveita o token pelo tempo de validade (55 min; a ANA dá 60);</li>
 *   <li>nunca tenta login de novo antes do intervalo mínimo (10 min), nem depois de reiniciar,
 *       porque a hora da última tentativa fica salva em disco;</li>
 *   <li>um login por vez (método sincronizado);</li>
 *   <li>o token nunca vai para o log.</li>
 * </ul>
 *
 * O arquivo de sessão fica em data/cache (fora do Git) e contém só o token (válido por 1 hora)
 * e horários; usuário e senha nunca são gravados.
 */
public class SessaoAna {

    private static final Logger log = LoggerFactory.getLogger(SessaoAna.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** O login foi recusado pela regra do intervalo mínimo. */
    public static class LoginAdiadoException extends RuntimeException {
        public LoginAdiadoException(Instant proximaTentativa) {
            super("Login na ANA adiado para não bloquear o IP; próxima tentativa a partir de " + proximaTentativa);
        }
    }

    private final Duration validade;
    private final Duration intervaloMinimoLogin;
    private final Path arquivo;
    private final Clock relogio;

    private String token;
    private Instant tokenObtidoEm;
    private Instant ultimaTentativaLogin;

    public SessaoAna(Duration validade, Duration intervaloMinimoLogin, Path arquivo, Clock relogio) {
        this.validade = validade;
        this.intervaloMinimoLogin = intervaloMinimoLogin;
        this.arquivo = arquivo;
        this.relogio = relogio;
        carregar();
    }

    /**
     * Devolve um token válido, fazendo login só se precisar e se for permitido.
     *
     * @param login faz o login e devolve o token (só é chamado quando realmente necessário)
     */
    public synchronized String token(Supplier<String> login) {
        Instant agora = relogio.instant();
        if (token != null && agora.isBefore(tokenObtidoEm.plus(validade))) {
            return token;
        }
        if (ultimaTentativaLogin != null && agora.isBefore(ultimaTentativaLogin.plus(intervaloMinimoLogin))) {
            throw new LoginAdiadoException(ultimaTentativaLogin.plus(intervaloMinimoLogin));
        }
        ultimaTentativaLogin = agora;
        token = null;
        salvar(); // registra a tentativa antes do login, mesmo que ele falhe
        String novo = login.get();
        token = novo;
        tokenObtidoEm = agora;
        salvar();
        log.info("ANA: login feito; token reaproveitado até {}", agora.plus(validade));
        return novo;
    }

    /** A ANA recusou o token (401): descarta. O próximo login continua respeitando o intervalo mínimo. */
    public synchronized void invalidar() {
        token = null;
        tokenObtidoEm = null;
        salvar();
    }

    public synchronized Optional<Instant> ultimaTentativaLogin() {
        return Optional.ofNullable(ultimaTentativaLogin);
    }

    public synchronized boolean temTokenValido() {
        return token != null && relogio.instant().isBefore(tokenObtidoEm.plus(validade));
    }

    private void carregar() {
        if (arquivo == null || !Files.exists(arquivo)) {
            return;
        }
        try {
            var no = MAPPER.readTree(arquivo.toFile());
            if (no.hasNonNull("ultimaTentativaLogin")) {
                ultimaTentativaLogin = Instant.parse(no.get("ultimaTentativaLogin").asText());
            }
            if (no.hasNonNull("token") && no.hasNonNull("tokenObtidoEm")) {
                token = no.get("token").asText();
                tokenObtidoEm = Instant.parse(no.get("tokenObtidoEm").asText());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("ANA: não foi possível ler a sessão salva ({})", e.getClass().getSimpleName());
        }
    }

    private void salvar() {
        if (arquivo == null) {
            return;
        }
        try {
            ObjectNode no = MAPPER.createObjectNode();
            no.put("token", token);
            no.put("tokenObtidoEm", tokenObtidoEm == null ? null : tokenObtidoEm.toString());
            no.put("ultimaTentativaLogin", ultimaTentativaLogin == null ? null : ultimaTentativaLogin.toString());
            Files.createDirectories(arquivo.toAbsolutePath().getParent());
            Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            MAPPER.writeValue(temporario.toFile(), no);
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("ANA: não foi possível salvar a sessão ({})", e.getClass().getSimpleName());
        }
    }
}
