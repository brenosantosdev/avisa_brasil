/* Partes de mapa usadas pela tela Mapa e pelo painel da Defesa Civil (Leaflet + OpenStreetMap). */
(function () {
  'use strict';

  const AB = window.AB;
  const t = AB.t;
  const ARQUIVO_SETORES = '/dados/sgb-setores-risco-sao-gabriel-2022.geojson';
  const ICONE_DO_TIPO = { ESCOLA: 'escola', SAUDE: 'saude', APOIO: 'predio', ABRIGO: 'abrigo' };

  /** Mapa com o mapa base do OpenStreetMap e a atribuição obrigatória. */
  function criarMapa(elemento, comunidade, opcoes) {
    const mapa = L.map(elemento, Object.assign({ scrollWheelZoom: true }, opcoes || {}))
      .setView([comunidade.latitude, comunidade.longitude], 15);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
    }).addTo(mapa);
    mapa.attributionControl.setPrefix('<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>');
    return mapa;
  }

  function htmlDoMarcador(classe, icone) {
    return '<div class="marcador ' + classe + '">' + AB.icone(icone) + '</div>';
  }

  function classeDoPonto(ponto) {
    const base = ponto.origem === 'DEFESA_CIVIL' ? 'marcador-dc' : 'marcador-' + ponto.tipo;
    return base + (ponto.setorDeRisco ? ' marcador-em-risco' : '');
  }

  function iconeDoPonto(ponto) {
    return ponto.origem === 'DEFESA_CIVIL' ? 'seguro' : ICONE_DO_TIPO[ponto.tipo];
  }

  function marcadorDoPonto(ponto) {
    const tamanho = ponto.origem === 'DEFESA_CIVIL' ? 44 : 36;
    return L.marker([ponto.latitude, ponto.longitude], {
      title: nomeDoPonto(ponto),
      alt: nomeDoPonto(ponto),
      icon: L.divIcon({
        className: '',
        html: htmlDoMarcador(classeDoPonto(ponto), iconeDoPonto(ponto)),
        iconSize: [tamanho, tamanho],
        iconAnchor: [tamanho / 2, tamanho / 2],
        popupAnchor: [0, -tamanho / 2]
      })
    });
  }

  function marcadorSimples(lat, lon, classe, icone, titulo) {
    return L.marker([lat, lon], {
      title: titulo,
      alt: titulo,
      icon: L.divIcon({ className: '', html: htmlDoMarcador(classe, icone), iconSize: [36, 36], iconAnchor: [18, 18] })
    });
  }

  function nomeDoPonto(ponto) {
    return ponto.nome || t('mapa.semNome', { tipo: t('tipo.' + ponto.tipo) });
  }

  /** Texto do balão de um ponto: nome, tipo, origem, distância e alerta de setor de risco. */
  function balaoDoPonto(ponto) {
    let html = '<h3>' + AB.escapar(nomeDoPonto(ponto)) + '</h3>';
    html += '<p>' + AB.escapar(t('tipo.' + ponto.tipo)) + ' · ' + AB.escapar(t('origem.' + ponto.origem)) + '</p>';
    html += '<p>' + AB.escapar(t('mapa.distancia', { d: AB.distancia(ponto.distanciaDaSedeMetros) })) + '</p>';
    if (ponto.capacidade) html += '<p>' + AB.escapar(t('mapa.capacidade', { n: AB.numero(ponto.capacidade) })) + '</p>';
    if (ponto.observacao) html += '<p>' + AB.escapar(ponto.observacao) + '</p>';
    if (ponto.setorDeRisco) {
      html += '<p class="balao-risco">' + AB.escapar(t('mapa.emSetor', { setor: ponto.setorDeRisco, grau: ponto.grauRiscoSetor })) + '</p>';
    }
    if (ponto.link) {
      html += '<p><a href="' + AB.escapar(ponto.link) + '" target="_blank" rel="noopener">' + AB.escapar(t('mapa.verNoOsm')) + '</a></p>';
    }
    return html;
  }

  /* ---------- Setores de risco oficiais (SGB) ---------- */

  async function carregarSetores() {
    const resposta = await fetch(ARQUIVO_SETORES);
    if (!resposta.ok) throw new Error('HTTP ' + resposta.status);
    return resposta.json();
  }

  function estiloDoSetor(feicao) {
    const muitoAlto = feicao.properties.grauRisco === 'Muito Alto';
    return {
      color: muitoAlto ? '#A3221B' : '#C2541A',
      weight: 2,
      dashArray: '6 4',
      fillColor: muitoAlto ? '#A3221B' : '#C2541A',
      fillOpacity: muitoAlto ? 0.35 : 0.25
    };
  }

  function balaoDoSetor(feicao, fonte) {
    const p = feicao.properties;
    let html = '<h3>' + AB.escapar(t('risco.titulo', { setor: p.setor })) + '</h3>';
    html += '<p class="balao-risco">' + AB.escapar(t('risco.grau', { grau: p.grauRisco.toLowerCase() })) + '</p>';
    html += '<p>' + AB.escapar(t('risco.tipos', { tipos: p.tipologia })) + '</p>';
    html += '<p>' + AB.escapar(t('risco.local', { local: p.local })) + '</p>';
    if (p.pessoasEstimadas) {
      html += '<p>' + AB.escapar(t('risco.pessoas', {
        ano: (p.dataVistoria || '').slice(0, 4), moradias: p.moradiasEstimadas, pessoas: p.pessoasEstimadas
      })) + '</p>';
    }
    if (p.descricao) html += '<p>' + AB.escapar(p.descricao) + '</p>';
    html += '<p><a href="' + AB.escapar(fonte.url) + '" target="_blank" rel="noopener">' + AB.escapar(t('risco.relatorio')) + '</a></p>';
    return html;
  }

  function camadaDeSetores(geojson) {
    return L.geoJSON(geojson, {
      style: estiloDoSetor,
      onEachFeature: function (feicao, camada) {
        camada.bindPopup(balaoDoSetor(feicao, geojson.fonte), { maxWidth: 320 });
      }
    });
  }

  /** "Setores de risco: Serviço Geológico do Brasil (SGB/CPRM), "Setorização..." (05/2022)." */
  function textoFonteSetores(geojson) {
    const f = geojson.fonte;
    return t('risco.fonte', { fonte: f.nome, titulo: f.titulo, data: f.publicacao.split('-').reverse().join('/') });
  }

  window.ABMapa = {
    criarMapa: criarMapa,
    marcadorDoPonto: marcadorDoPonto,
    marcadorSimples: marcadorSimples,
    htmlDoMarcador: htmlDoMarcador,
    classeDoPonto: classeDoPonto,
    iconeDoPonto: iconeDoPonto,
    nomeDoPonto: nomeDoPonto,
    balaoDoPonto: balaoDoPonto,
    carregarSetores: carregarSetores,
    camadaDeSetores: camadaDeSetores,
    textoFonteSetores: textoFonteSetores
  };
})();
