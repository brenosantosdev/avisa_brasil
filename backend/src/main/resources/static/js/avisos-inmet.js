/* AvisaBrasil: avisos oficiais do INMET (usado no Início e em Entenda os riscos).
   Só aparecem os avisos em que o INMET inclui o município (código do IBGE no aviso completo). */
(function () {
  'use strict';

  const t = AB.t;
  /* Cores do INMET: amarelo, laranja e vermelho (as mesmas classes do semáforo, sempre com ícone e palavra). */
  const NIVEL_DO_GRAU = { PERIGO_POTENCIAL: 'ATENCAO', PERIGO: 'ALERTA', GRANDE_PERIGO: 'EMERGENCIA' };

  let ultima = null;

  async function carregar() {
    try {
      const resposta = await AB.buscar('/api/avisos-inmet', { cache: 'no-store' }, true); // sem conexão: o aviso diz de quando é o dado
      ultima = resposta.ok ? await resposta.json() : null;
    } catch (e) {
      ultima = null;
    }
    return ultima;
  }

  function nomeDoEvento(a) {
    const chave = 'inmet.evento.' + a.eventoCodigo;
    const texto = t(chave);
    return texto === chave ? a.evento : texto;
  }

  function nomeDoGrau(a) {
    return a.grau ? t('inmet.grau.' + a.grau) : '';
  }

  function textoDoPeriodo(a) {
    return t('inmet.periodo', { inicio: AB.quando(a.inicio), fim: AB.quando(a.fim) });
  }

  function etiqueta(a) {
    if (!a.grau) return '';
    const nivel = NIVEL_DO_GRAU[a.grau];
    return '<span class="etiqueta nivel-' + nivel + '">' + AB.icone(nivel === 'ATENCAO' ? 'info' : 'alerta') +
      AB.escapar('INMET · ' + nomeDoGrau(a)) + '</span>';
  }

  function cartaoAviso(a) {
    const emPortugues = AB.idioma() === 'pt';
    return '<article class="aviso-inmet">' +
      '<div class="aviso-inmet-topo">' + etiqueta(a) + '<h3>' + AB.escapar(nomeDoEvento(a)) + '</h3></div>' +
      '<p class="aviso-inmet-quando">' + AB.icone('info') + '<span>' + AB.escapar(textoDoPeriodo(a)) + '</span></p>' +
      (emPortugues ? '' : '<p class="nota">' + AB.escapar(t('inmet.textoOficial')) + '</p>') +
      '<p lang="pt-BR">' + AB.escapar(a.descricao) + '</p>' +
      (a.instrucao ? '<p class="nota"><strong>' + AB.escapar(t('inmet.instrucao')) + '</strong> <span lang="pt-BR">' +
        AB.escapar(a.instrucao) + '</span></p>' : '') +
      (a.link ? '<p><a href="' + AB.escapar(a.link) + '" target="_blank" rel="noopener">' + AB.escapar(t('inmet.verAviso')) + '</a></p>' : '') +
      '</article>';
  }

  /** Desenha a seção dentro de "alvo" (um <section>). */
  function renderizar(alvo, resposta) {
    if (!alvo) return;
    const d = resposta && resposta.dados;
    let html = '<h2 id="titulo-inmet">' + AB.icone('alerta') + AB.escapar(t('inmet.titulo')) + '</h2>';
    html += AB.avisoSituacao(resposta);
    if (!d) {
      alvo.innerHTML = html + (resposta ? AB.rodapeFonte(resposta) : '<p>' + AB.escapar(t('situacao.INDISPONIVEL')) + '</p>');
      alvo.classList.remove('com-aviso');
      return;
    }
    const lugar = d.municipio;
    if (d.ativos.length) {
      html += d.ativos.map(cartaoAviso).join('');
    } else {
      html += '<p class="inmet-nenhum">' + AB.icone('seguro') + '<span>' + AB.escapar(t('inmet.nenhum', { lugar: lugar })) + '</span></p>';
    }
    if (d.recentes.length) {
      html += '<details class="inmet-recentes"><summary>' +
        AB.escapar(t('inmet.recentes', { dias: d.diasRecentes, lugar: lugar, n: d.recentes.length })) + '</summary><ul>' +
        d.recentes.map(function (a) {
          return '<li><strong>' + AB.escapar(nomeDoEvento(a)) + '</strong>' + (a.grau ? ' · ' + AB.escapar(nomeDoGrau(a)) : '') +
            ' · ' + AB.escapar(textoDoPeriodo(a)) + '</li>';
        }).join('') + '</ul></details>';
    } else {
      html += '<p class="nota">' + AB.escapar(t('inmet.recentes.nenhum', { dias: d.diasRecentes, lugar: lugar })) + '</p>';
    }
    html += AB.rodapeFonte(resposta, '<p>' + AB.escapar(t('inmet.filtro', { lugar: lugar, codigo: d.codigoIbge })) + '</p>');
    alvo.innerHTML = html;
    alvo.classList.toggle('com-aviso', d.ativos.length > 0);
  }

  /** Texto para o botão Ouvir. */
  function textoParaOuvir(resposta, mesmoSemAviso) {
    const d = resposta && resposta.dados;
    if (!d) return '';
    if (!d.ativos.length) return mesmoSemAviso ? t('inmet.ouvir.nenhum') : '';
    return d.ativos.map(function (a) {
      return t('inmet.ouvir', { evento: nomeDoEvento(a), grau: nomeDoGrau(a), periodo: textoDoPeriodo(a) });
    }).join(' ');
  }

  window.AvisosInmet = {
    carregar: carregar,
    renderizar: renderizar,
    textoParaOuvir: textoParaOuvir,
    ultima: function () { return ultima; }
  };
})();
