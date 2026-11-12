/* Tela "Mapa": pontos de apoio reais (OSM + Defesa Civil) e setores de risco oficiais (SGB). */
(function () {
  'use strict';

  const AB = window.AB;
  const ABMapa = window.ABMapa;
  const t = AB.t;
  /** Abaixo disso, a tela avisa que o OpenStreetMap tem poucos pontos mapeados. */
  const POUCOS_PONTOS_OSM = 20;

  const estado = {
    comunidade: null, mapa: null, resposta: null, setores: null,
    camadaPontos: null, camadaRisco: null, marcadores: {}, maisPerto: null
  };

  /* ---------- Camadas ---------- */

  function desenharPontos() {
    if (estado.camadaPontos) estado.camadaPontos.remove();
    estado.camadaPontos = L.layerGroup();
    estado.marcadores = {};
    const pontos = estado.resposta && estado.resposta.dados ? estado.resposta.dados.pontos : [];
    pontos.forEach(function (p) {
      const marcador = ABMapa.marcadorDoPonto(p).bindPopup(ABMapa.balaoDoPonto(p), { maxWidth: 300 });
      // pontos da Defesa Civil ficam por cima
      if (p.origem === 'DEFESA_CIVIL') marcador.setZIndexOffset(1000);
      marcador.addTo(estado.camadaPontos);
      estado.marcadores[p.id] = marcador;
    });
    if (document.getElementById('camada-pontos').checked) estado.camadaPontos.addTo(estado.mapa);
  }

  function desenharSetores() {
    if (!estado.setores) return;
    estado.camadaRisco = ABMapa.camadaDeSetores(estado.setores);
    if (document.getElementById('camada-risco').checked) {
      estado.camadaRisco.addTo(estado.mapa);
      estado.camadaRisco.bringToBack();
    }
  }

  function enquadrar() {
    const limites = L.latLngBounds([[estado.comunidade.latitude, estado.comunidade.longitude]]);
    Object.values(estado.marcadores).forEach(function (m) { limites.extend(m.getLatLng()); });
    if (estado.camadaRisco) limites.extend(estado.camadaRisco.getBounds());
    estado.mapa.fitBounds(limites, { padding: [30, 30], maxZoom: 16 });
  }

  /* ---------- Legenda, lista e fontes ---------- */

  function montarLegenda() {
    // ponto da Defesa Civil sempre aparece (explica o verde); dos tipos do OSM, só os que existem no mapa
    const pontos = estado.resposta && estado.resposta.dados ? estado.resposta.dados.pontos : [];
    const tiposOsm = ['ESCOLA', 'SAUDE', 'APOIO', 'ABRIGO'].filter(function (tipo) {
      return pontos.some(function (p) { return p.origem === 'OPENSTREETMAP' && p.tipo === tipo; });
    });
    const itens = [[ABMapa.htmlDoMarcador('marcador-dc', 'seguro'), t('mapa.legenda.defesaCivil')]]
      .concat(tiposOsm.map(function (tipo) {
        return [ABMapa.htmlDoMarcador('marcador-' + tipo, ABMapa.iconeDoPonto({ tipo: tipo })), t('mapa.legenda.' + tipo)];
      }))
      .concat([[ABMapa.htmlDoMarcador('marcador-sede', 'local'), t('mapa.sede', { lugar: estado.comunidade.nome })]]);
    if (estado.setores) {
      itens.push(['<span class="amostra-risco amostra-risco-muito-alto"></span>', t('mapa.legenda.riscoMuitoAlto')]);
      itens.push(['<span class="amostra-risco amostra-risco-alto"></span>', t('mapa.legenda.riscoAlto')]);
    }
    document.getElementById('legenda').innerHTML = itens.map(function (i) {
      return '<li>' + i[0] + '<span>' + AB.escapar(i[1]) + '</span></li>';
    }).join('');
  }

  function montarLista() {
    const alvo = document.getElementById('lista');
    const dados = estado.resposta && estado.resposta.dados;
    if (!dados) {
      alvo.innerHTML = '';
      return;
    }
    let html = '<p>' + AB.escapar(t('mapa.lista.resumo', { dc: dados.totalDefesaCivil, osm: dados.totalOpenStreetMap })) + '</p>';
    if (dados.totalDefesaCivil === 0) html += '<p class="nota">' + AB.escapar(t('mapa.nenhumDc')) + '</p>';
    if (dados.totalOpenStreetMap < POUCOS_PONTOS_OSM) {
      html += '<p class="aviso-info">' + AB.icone('info') + '<span>' +
        AB.escapar(t('mapa.poucos', { n: dados.totalOpenStreetMap })) + '</span></p>';
    }
    html += '<ul class="lista-pontos">' + dados.pontos.map(function (p) {
      let detalhe = t('tipo.' + p.tipo) + ' · ' + t('origem.' + p.origem) + ' · ' +
        t('mapa.distancia', { d: AB.distancia(p.distanciaDaSedeMetros) });
      return '<li>' + ABMapa.htmlDoMarcador(ABMapa.classeDoPonto(p), ABMapa.iconeDoPonto(p)) +
        '<div class="texto"><p class="nome">' + AB.escapar(ABMapa.nomeDoPonto(p)) + '</p>' +
        '<p class="detalhe">' + AB.escapar(detalhe) + '</p>' +
        (p.setorDeRisco ? '<p class="detalhe balao-risco">' +
          AB.escapar(t('mapa.emSetor', { setor: p.setorDeRisco, grau: p.grauRiscoSetor })) + '</p>' : '') +
        '</div><button class="botao botao-secundario" type="button" data-ponto="' + AB.escapar(p.id) + '">' +
        AB.icone('local') + AB.escapar(t('mapa.verNoMapa')) + '</button></li>';
    }).join('') + '</ul>';
    alvo.innerHTML = html;
  }

  function montarFontes() {
    const r = estado.resposta;
    document.getElementById('aviso-situacao').innerHTML = AB.avisoSituacao(r);
    let html = '<div class="fonte">';
    html += '<p class="aviso-info">' + AB.icone('info') + '<span>' + AB.escapar(t('mapa.naoOficial')) + '</span></p>';
    html += '<p>' + AB.escapar(t('fonte.mapaBase')) + ' (<a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">openstreetmap.org/copyright</a>)</p>';
    if (r && r.dados && r.dados.dadosOsmDe) {
      html += '<p>' + AB.escapar(t('fonte.pontos', { quando: AB.quando(r.dados.dadosOsmDe) })) +
        ' · ' + AB.escapar(t('fonte.atualizado', { quando: AB.quando(r.atualizadoEm) })) +
        ' · <a href="' + AB.escapar(r.fonteUrl) + '" target="_blank" rel="noopener">' + AB.escapar(r.fonte) + '</a></p>';
    }
    if (estado.setores) {
      html += '<p>' + AB.escapar(ABMapa.textoFonteSetores(estado.setores)) +
        ' <a href="' + AB.escapar(estado.setores.fonte.url) + '" target="_blank" rel="noopener">rigeo.sgb.gov.br</a></p>';
    }
    document.getElementById('fontes').innerHTML = html + '</div>';
  }

  /* ---------- Minha localização ---------- */

  function distanciaMetros(lat1, lon1, lat2, lon2) {
    return L.latLng(lat1, lon1).distanceTo(L.latLng(lat2, lon2));
  }

  function localizar() {
    const alvo = document.getElementById('resultado-localizacao');
    if (!navigator.geolocation) {
      alvo.innerHTML = '<p>' + AB.escapar(t('mapa.semLocalizacao')) + '</p>';
      return;
    }
    alvo.innerHTML = '<p>' + AB.escapar(t('mapa.localizando')) + '</p>';
    navigator.geolocation.getCurrentPosition(function (posicao) {
      const lat = posicao.coords.latitude;
      const lon = posicao.coords.longitude;
      if (estado.voce) estado.voce.remove();
      estado.voce = ABMapa.marcadorSimples(lat, lon, 'marcador-voce', 'pessoa', t('mapa.voce'))
        .bindPopup(AB.escapar(t('mapa.voce'))).addTo(estado.mapa);

      // o mais perto que não fica em setor de risco
      const pontos = (estado.resposta.dados ? estado.resposta.dados.pontos : []).filter(function (p) { return !p.setorDeRisco; });
      if (!pontos.length) {
        estado.mapa.setView([lat, lon], 16);
        alvo.innerHTML = '';
        return;
      }
      const perto = pontos.map(function (p) {
        return { ponto: p, d: distanciaMetros(lat, lon, p.latitude, p.longitude) };
      }).sort(function (a, b) { return a.d - b.d; })[0];
      estado.maisPerto = perto;
      estado.mapa.fitBounds(L.latLngBounds([[lat, lon], [perto.ponto.latitude, perto.ponto.longitude]]), { padding: [50, 50], maxZoom: 17 });
      estado.marcadores[perto.ponto.id].openPopup();
      const rota = 'https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=' +
        encodeURIComponent(lat.toFixed(5) + ',' + lon.toFixed(5) + ';' + perto.ponto.latitude.toFixed(5) + ',' + perto.ponto.longitude.toFixed(5));
      alvo.innerHTML = '<p><strong>' + AB.escapar(t('mapa.maisPerto', { nome: ABMapa.nomeDoPonto(perto.ponto), d: AB.distancia(perto.d) })) + '</strong></p>' +
        '<p class="nota">' + AB.escapar(t('mapa.maisPerto.aviso')) + '</p>' +
        '<p><a class="botao botao-secundario botao-largo" href="' + rota + '" target="_blank" rel="noopener">' + AB.escapar(t('mapa.caminho')) + '</a></p>';
    }, function () {
      alvo.innerHTML = '<p>' + AB.escapar(t('mapa.semLocalizacao')) + '</p>';
    }, { enableHighAccuracy: true, timeout: 15000 });
  }

  /* ---------- Voz ---------- */

  function textoParaOuvir() {
    const partes = [t('mapa.titulo') + '. ' + document.getElementById('intro').textContent];
    const dados = estado.resposta && estado.resposta.dados;
    if (dados) {
      partes.push(t('mapa.lista.resumo', { dc: dados.totalDefesaCivil, osm: dados.totalOpenStreetMap }));
      dados.pontos.slice(0, 3).forEach(function (p) {
        partes.push(ABMapa.nomeDoPonto(p) + ', ' + t('mapa.distancia', { d: AB.distancia(p.distanciaDaSedeMetros) }) + '.');
      });
      partes.push(t('mapa.naoOficial'));
    }
    if (estado.maisPerto) {
      partes.push(t('mapa.maisPerto', { nome: ABMapa.nomeDoPonto(estado.maisPerto.ponto), d: AB.distancia(estado.maisPerto.d) }));
    }
    return partes.join(' ');
  }

  /* ---------- Início ---------- */

  async function iniciar() {
    estado.comunidade = await AB.iniciarPagina();
    document.getElementById('intro').textContent = t('mapa.intro', { lugar: estado.comunidade.nome });
    document.getElementById('botao-localizacao').innerHTML = AB.icone('pessoa') + AB.escapar(t('mapa.minhaLocalizacao'));
    AB.configurarOuvir(textoParaOuvir);

    estado.mapa = ABMapa.criarMapa('mapa', estado.comunidade);
    document.getElementById('mapa').setAttribute('aria-label', t('mapa.rotulo', { lugar: estado.comunidade.nome }));
    ABMapa.marcadorSimples(estado.comunidade.latitude, estado.comunidade.longitude, 'marcador-sede', 'local',
      t('mapa.sede', { lugar: estado.comunidade.nome }))
      .bindPopup(AB.escapar(t('mapa.sede', { lugar: estado.comunidade.nome }))).addTo(estado.mapa);

    const [resposta, setores] = await Promise.all([
      fetch('/api/mapa/pontos', { cache: 'no-store' }).then(function (r) { return r.json(); })
        .catch(function () { return { situacao: 'INDISPONIVEL', dados: null }; }),
      ABMapa.carregarSetores().catch(function () { return null; })
    ]);
    estado.resposta = resposta;
    estado.setores = setores;

    desenharSetores();
    desenharPontos();
    montarLegenda();
    montarLista();
    montarFontes();
    enquadrar();

    document.getElementById('camada-risco').addEventListener('change', function (e) {
      if (!estado.camadaRisco) return;
      if (e.target.checked) { estado.camadaRisco.addTo(estado.mapa); estado.camadaRisco.bringToBack(); }
      else estado.camadaRisco.remove();
    });
    document.getElementById('camada-pontos').addEventListener('change', function (e) {
      if (e.target.checked) estado.camadaPontos.addTo(estado.mapa); else estado.camadaPontos.remove();
    });
    document.getElementById('botao-localizacao').addEventListener('click', localizar);
    document.getElementById('lista').addEventListener('click', function (e) {
      const botao = e.target.closest('[data-ponto]');
      if (!botao) return;
      const marcador = estado.marcadores[botao.dataset.ponto];
      if (!document.getElementById('camada-pontos').checked) {
        document.getElementById('camada-pontos').checked = true;
        estado.camadaPontos.addTo(estado.mapa);
      }
      estado.mapa.setView(marcador.getLatLng(), 17);
      marcador.openPopup();
      document.getElementById('mapa').scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  iniciar();
})();
