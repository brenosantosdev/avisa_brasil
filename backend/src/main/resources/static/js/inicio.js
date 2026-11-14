/* Tela Início: semáforo da comunidade (maior risco entre cheia e seca) e as 3 perguntas. */
(function () {
  'use strict';

  const AB = window.AB;
  const t = AB.t;
  const NIVEIS = ['NORMAL', 'ATENCAO', 'ALERTA', 'EMERGENCIA'];
  const ICONE = { NORMAL: 'seguro', ATENCAO: 'info', ALERTA: 'alerta', EMERGENCIA: 'alerta' };
  const ATUALIZAR_A_CADA_MS = 30 * 1000;

  let risco = null;

  function chaveDoNivel(r) {
    return r.nivel === 'NORMAL' ? 'NORMAL' : r.tipo + '.' + r.nivel;
  }

  /* ---------- Estou seguro? ---------- */

  function renderizarSemaforo(r) {
    const alvo = document.getElementById('semaforo');
    if (!r) {
      alvo.className = 'semaforo';
      alvo.innerHTML = '<div class="aviso-situacao aviso-INDISPONIVEL">' + AB.icone('alerta') + '<span>' +
        AB.escapar(t('inicio.indisponivel')) + '</span></div>';
      return;
    }
    alvo.className = 'semaforo nivel-' + r.nivel;
    const luzes = NIVEIS.slice().reverse().map(function (n) {
      return '<span class="luz l-' + n + (n === r.nivel ? ' acesa' : '') + '" aria-hidden="true">' + AB.icone(ICONE[n]) + '</span>';
    }).join('');
    alvo.innerHTML = '<div class="luzes">' + luzes + '</div><div>' +
      '<p class="pergunta" id="pergunta-seguro">' + AB.escapar(t('inicio.perguntaSeguro')) + '</p>' +
      '<p class="palavra-nivel">' + AB.icone(ICONE[r.nivel]) + '<span>' + AB.escapar(t('nivel.' + r.nivel)) +
      (r.tipo ? ' · ' + AB.escapar(t('tipoRisco.' + r.tipo)) : '') + '</span></p>' +
      '<p class="resposta">' + AB.escapar(t('seguro.' + chaveDoNivel(r))) + '</p></div>';
  }

  /* ---------- O que está acontecendo? ---------- */

  function renderizarAcontecendo(r) {
    const alvo = document.getElementById('acontecendo');
    if (!r) { alvo.innerHTML = ''; return; }
    let html = '';
    if (r.principal) {
      html += '<p class="motivo-principal">' + AB.escapar(AB.textoFator(r.principal)) + '</p>';
      const outros = r.fatores.filter(function (f) { return f !== r.principal && JSON.stringify(f) !== JSON.stringify(r.principal); });
      if (outros.length) {
        html += '<p class="rotulo">' + AB.escapar(t('inicio.outrosSinais')) + '</p><ul class="fatores">' + outros.map(function (f) {
          return '<li>' + AB.etiquetaNivel(f.nivel, f.tipo) + '<span>' + AB.escapar(AB.textoFator(f)) + '</span></li>';
        }).join('') + '</ul>';
      }
    } else {
      html += '<p class="motivo-principal">' + AB.escapar(t('inicio.semRisco')) + '</p>';
    }
    html += '<div class="atalhos">' +
      '<a class="botao botao-primario" href="/rio-agora.html">' + AB.icone('regua') + AB.escapar(t('inicio.verRio')) + '</a>' +
      '<a class="botao botao-secundario" href="/mapa.html">' + AB.icone('local') + AB.escapar(t('inicio.verMapa')) + '</a>' +
      '<a class="botao botao-secundario" href="/proximos-dias.html">' + AB.icone('chuva') + AB.escapar(t('inicio.verDias')) + '</a></div>';
    alvo.innerHTML = html;
  }

  /* ---------- O que eu faço agora? (checklist guardado neste aparelho) ---------- */

  function lerMarcados(chave) {
    try { return JSON.parse(localStorage.getItem('checklist.' + chave) || '[]'); } catch (e) { return []; }
  }

  function salvarMarcados(chave, marcados) {
    try { localStorage.setItem('checklist.' + chave, JSON.stringify(marcados)); } catch (e) { /* sem armazenamento */ }
  }

  function checklist(alvo, chave, itens) {
    const marcados = lerMarcados(chave);
    alvo.innerHTML = itens.map(function (item, i) {
      return '<li><label><input type="checkbox" data-i="' + i + '"' + (marcados.includes(i) ? ' checked' : '') + '><span>' +
        AB.escapar(item) + '</span></label></li>';
    }).join('');
    alvo.onchange = function () {
      salvarMarcados(chave, Array.from(alvo.querySelectorAll('input:checked')).map(function (c) { return Number(c.dataset.i); }));
    };
  }

  function renderizarFazer(r) {
    const alvo = document.getElementById('fazer');
    if (!r) { alvo.innerHTML = ''; return; }
    const chave = chaveDoNivel(r);
    alvo.innerHTML = '<p>' + AB.etiquetaNivel(r.nivel, r.tipo) + '</p><p class="nota">' + AB.escapar(t('inicio.marcar')) +
      '</p><ul class="checklist" id="acoes"></ul>';
    checklist(document.getElementById('acoes'), 'acoes.' + chave, AB.lista('acoes.' + chave));
  }

  /* ---------- Legenda, simulação e fontes ---------- */

  function renderizarLegenda() {
    document.getElementById('legenda').innerHTML = NIVEIS.map(function (n) {
      return '<li class="nivel-' + n + '"><strong>' + AB.icone(ICONE[n]) + AB.escapar(t('nivel.' + n)) + '</strong><span>' +
        AB.escapar(t('legenda.' + n)) + '</span></li>';
    }).join('');
  }

  function renderizarSimulacao(r) {
    document.getElementById('simulacao').innerHTML = r && r.simulado
      ? '<p class="aviso-simulacao" role="alert">' + AB.icone('alerta') + '<span>' + AB.escapar(t('inicio.simulacao')) + '</span></p>'
      : '';
  }

  function renderizarFontes(r) {
    if (!r) { document.getElementById('fontes').innerHTML = ''; return; }
    let html = '<div class="fonte"><p>' + AB.escapar(t('inicio.avaliado', { quando: AB.quando(r.avaliadoEm) })) + '</p>' +
      '<p>' + AB.escapar(t('inicio.fontes')) + '</p>';
    const c = r.cotas;
    if (c && (c.cheia.origem === 'PROVISORIA' || c.seca.origem === 'PROVISORIA')) {
      html += '<p><strong>' + AB.escapar(t('inicio.cotasProvisorias')) + '</strong> <a href="/rio-agora.html">' + AB.escapar(t('nav.rioAgora')) + '</a></p>';
    }
    document.getElementById('fontes').innerHTML = html + '</div>';
  }

  /* ---------- Voz ---------- */

  function textoParaOuvir() {
    const r = risco;
    if (!r) return t('inicio.indisponivel');
    const partes = [];
    if (r.simulado) partes.push(t('inicio.simulacao'));
    partes.push(t('inicio.perguntaSeguro') + ' ' + t('nivel.' + r.nivel) + (r.tipo ? ', ' + t('tipoRisco.' + r.tipo) : '') + '. ' +
      t('seguro.' + chaveDoNivel(r)));
    partes.push(t('inicio.perguntaAcontecendo') + ' ' + (r.principal ? AB.textoFator(r.principal) : t('inicio.semRisco')));
    const inmet = AvisosInmet.textoParaOuvir(AvisosInmet.ultima(), false);
    if (inmet) partes.push(inmet);
    partes.push(t('inicio.perguntaFazer') + ' ' + AB.lista('acoes.' + chaveDoNivel(r)).join(' '));
    return partes.join(' ');
  }

  async function carregar() {
    try {
      const resposta = await AB.buscar('/api/risco', { cache: 'no-store' }, true);
      risco = resposta.ok ? await resposta.json() : null;
    } catch (e) {
      risco = null;
    }
    renderizarSimulacao(risco);
    renderizarSemaforo(risco);
    renderizarAcontecendo(risco);
    renderizarFazer(risco);
    renderizarFontes(risco);
    AvisosInmet.renderizar(document.getElementById('avisos-inmet'), await AvisosInmet.carregar());
  }

  async function iniciar() {
    await AB.iniciarPagina();
    AB.configurarOuvir(textoParaOuvir);
    renderizarLegenda();
    checklist(document.getElementById('mochila'), 'mochila', AB.lista('mochila'));
    await carregar();
    setInterval(carregar, ATUALIZAR_A_CADA_MS);
    AB.aoVoltarConexao(carregar);
  }

  iniciar();
})();
