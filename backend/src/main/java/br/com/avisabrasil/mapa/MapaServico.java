package br.com.avisabrasil.mapa;

import br.com.avisabrasil.config.AvisaBrasilProperties;
import br.com.avisabrasil.dominio.PontoSeguro;
import br.com.avisabrasil.integracoes.CacheDeFonte;
import br.com.avisabrasil.integracoes.OverpassClient;
import br.com.avisabrasil.integracoes.RespostaComFonte;
import br.com.avisabrasil.repositorio.PontoSeguroRepositorio;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Junta os pontos de apoio do OpenStreetMap (cache, atualizado 1 vez por dia) com os pontos
 * seguros cadastrados pela Defesa Civil, e marca os que ficam dentro de um setor de risco do SGB.
 */
@Service
public class MapaServico {

    private final OverpassClient overpass;
    private final PontoSeguroRepositorio repositorio;
    private final SetoresDeRisco setores;
    private final AvisaBrasilProperties propriedades;
    private final Clock relogio;
    private final CacheDeFonte<PontosOsm> cache;

    public MapaServico(OverpassClient overpass, PontoSeguroRepositorio repositorio, SetoresDeRisco setores,
            AvisaBrasilProperties propriedades, Clock relogio) {
        this.overpass = overpass;
        this.repositorio = repositorio;
        this.setores = setores;
        this.propriedades = propriedades;
        this.relogio = relogio;
        var fontes = propriedades.fontes();
        this.cache = new CacheDeFonte<>(
                OverpassClient.FONTE,
                fontes.overpass().intervalo(),
                fontes.novaTentativa(),
                fontes.pastaCache().resolve("overpass-pontos.json"),
                OverpassConversor::converter,
                relogio);
    }

    public void atualizarSeNecessario() {
        if (cache.deveAtualizar()) {
            atualizar();
        }
    }

    public void atualizar() {
        cache.atualizar(overpass::buscarPontos);
    }

    @Transactional(readOnly = true)
    public RespostaComFonte<PontosDoMapa> consultar() {
        List<PontoDeApoio> pontos = new ArrayList<>();
        List<PontoDeApoio> daDefesaCivil = listarCadastrados();
        pontos.addAll(daDefesaCivil);
        Optional<PontosOsm> osm = cache.valor();
        osm.ifPresent(o -> o.pontos().forEach(p -> pontos.add(
                montar(p.id(), p.nome(), p.tipo(), OrigemPonto.OPENSTREETMAP, p.latitude(), p.longitude(),
                        null, null, p.link()))));
        pontos.sort(Comparator.comparing((PontoDeApoio p) -> p.origem() != OrigemPonto.DEFESA_CIVIL)
                .thenComparingLong(PontoDeApoio::distanciaDaSedeMetros));

        var dados = new PontosDoMapa(
                osm.map(PontosOsm::dadosOsmDe).map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                osm.map(o -> o.pontos().size()).orElse(0),
                daDefesaCivil.size(),
                pontos);
        return new RespostaComFonte<>(
                OverpassClient.FONTE,
                OverpassClient.FONTE_URL,
                cache.obtidoEm().map(i -> i.atZone(relogio.getZone()).toOffsetDateTime()).orElse(null),
                cache.situacao(),
                cache.ultimoErro().orElse(null),
                dados);
    }

    @Transactional(readOnly = true)
    public List<PontoDeApoio> listarCadastrados() {
        return repositorio.findByAtivoTrueOrderByNomeAsc().stream().map(this::paraPonto).toList();
    }

    @Transactional
    public PontoDeApoio cadastrar(NovoPontoSeguro novo) {
        var ponto = new PontoSeguro(
                novo.nome().strip(),
                novo.tipo(),
                novo.latitude(),
                novo.longitude(),
                novo.observacao() == null || novo.observacao().isBlank() ? null : novo.observacao().strip(),
                novo.capacidade(),
                relogio.instant());
        return paraPonto(repositorio.save(ponto));
    }

    /** Tira o ponto do mapa. O registro fica guardado, marcado como inativo. */
    @Transactional
    public boolean remover(long id) {
        return repositorio.findByIdAndAtivoTrue(id).map(p -> {
            p.desativar(relogio.instant());
            return true;
        }).orElse(false);
    }

    private PontoDeApoio paraPonto(PontoSeguro p) {
        return montar("dc:" + p.getId(), p.getNome(), p.getTipo(), OrigemPonto.DEFESA_CIVIL,
                p.getLatitude(), p.getLongitude(), p.getObservacao(), p.getCapacidade(), null);
    }

    private PontoDeApoio montar(String id, String nome, TipoPonto tipo, OrigemPonto origem, double lat, double lon,
            String observacao, Integer capacidade, String link) {
        var sede = propriedades.comunidade();
        Optional<SetoresDeRisco.Setor> setor = setores.setorEm(lat, lon);
        return new PontoDeApoio(id, nome, tipo, origem, lat, lon, observacao, capacidade, link,
                setor.map(SetoresDeRisco.Setor::codigo).orElse(null),
                setor.map(SetoresDeRisco.Setor::grauRisco).orElse(null),
                Math.round(Geo.distanciaMetros(sede.latitude(), sede.longitude(), lat, lon)));
    }
}
