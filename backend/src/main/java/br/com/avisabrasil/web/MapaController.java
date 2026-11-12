package br.com.avisabrasil.web;

import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.mapa.MapaServico;
import br.com.avisabrasil.mapa.NovoPontoSeguro;
import br.com.avisabrasil.mapa.PontoDeApoio;
import br.com.avisabrasil.mapa.PontosDoMapa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Tag(name = "Mapa", description = "Pontos de apoio reais (OpenStreetMap) e pontos seguros cadastrados pela Defesa Civil")
public class MapaController {

    private final MapaServico mapa;

    public MapaController(MapaServico mapa) {
        this.mapa = mapa;
    }

    @GetMapping("/mapa/pontos")
    @Operation(summary = "Todos os pontos do mapa",
            description = "Pontos cadastrados pela Defesa Civil primeiro, depois os do OpenStreetMap, por distância da sede. "
                    + "Cada ponto informa se fica dentro de um setor de risco do SGB (2022).")
    public RespostaComFonte<PontosDoMapa> pontos() {
        return mapa.consultar();
    }

    @GetMapping("/pontos-seguros")
    @Operation(summary = "Pontos seguros cadastrados pela Defesa Civil")
    public List<PontoDeApoio> cadastrados() {
        return mapa.listarCadastrados();
    }

    @PostMapping("/pontos-seguros")
    @Operation(summary = "Cadastrar ponto seguro (painel da Defesa Civil)")
    public ResponseEntity<PontoDeApoio> cadastrar(@Valid @RequestBody NovoPontoSeguro novo) {
        PontoDeApoio criado = mapa.cadastrar(novo);
        String id = criado.id().substring("dc:".length());
        return ResponseEntity.created(URI.create("/api/pontos-seguros/" + id)).body(criado);
    }

    @DeleteMapping("/pontos-seguros/{id}")
    @Operation(summary = "Tirar um ponto seguro do mapa (fica guardado como inativo)")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        if (!mapa.remover(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ponto seguro não encontrado");
        }
        return ResponseEntity.noContent().build();
    }
}
