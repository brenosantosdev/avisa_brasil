/* Tela "Entenda os riscos": cheia, seca, calor extremo e deslizamento, com os avisos do INMET no topo.
   Os textos ficam em i18n/<idioma>.json (riscos.<RISCO>.*). Funciona sem internet (service worker). */
(function () {
  'use strict';

  const t = AB.t;

  const RISCOS = [
    { id: 'CHEIA', icone: 'cheia', link: '/rio-agora.html', cor: 'risco-cheia' },
    { id: 'SECA', icone: 'seca', link: '/rio-agora.html', cor: 'risco-seca' },
    { id: 'CALOR', icone: 'termometro', link: '/proximos-dias.html', cor: 'risco-calor' },
    { id: 'DESLIZAMENTO', icone: 'deslizamento', link: '/mapa.html', cor: 'risco-deslizamento' }
  ];

  let comunidade = { nome: '' };

  function texto(r, parte) {
    return t('riscos.' + r.id + '.' + parte, { lugar: comunidade.nome });
  }

  function lista(r, parte) {
    return AB.lista('riscos.' + r.id + '.' + parte);
  }

  function renderizarIndice() {
    document.getElementById('indice-riscos').innerHTML = RISCOS.map(function (r) {
      return '<a class="botao-risco ' + r.cor + '" href="#risco-' + r.id + '">' + AB.icone(r.icone) +
        '<span>' + AB.escapar(texto(r, 'nome')) + '</span></a>';
    }).join('');
  }

  function cartao(r) {
    const nome = texto(r, 'nome');
    return '<section class="cartao secao risco ' + r.cor + '" id="risco-' + r.id + '" aria-labelledby="titulo-' + r.id + '">' +
      '<div class="risco-topo"><h2 id="titulo-' + r.id + '">' + AB.icone(r.icone) + AB.escapar(nome) + '</h2>' +
      '<button type="button" class="botao botao-secundario botao-ouvir-risco" data-risco="' + r.id + '"><span>' +
      AB.escapar(t('ouvir')) + '</span></button></div>' +
      '<h3>' + AB.escapar(t('riscos.oQueE')) + '</h3><p>' + AB.escapar(texto(r, 'oQueE')) + '</p>' +
      '<h3>' + AB.escapar(t('riscos.sinais')) + '</h3><ul class="lista-sinais">' + lista(r, 'sinais').map(function (s) {
        return '<li>' + AB.icone('alerta') + '<span>' + AB.escapar(s) + '</span></li>';
      }).join('') + '</ul>' +
      '<h3>' + AB.escapar(t('riscos.proteger')) + '</h3><ul class="lista-proteger">' + lista(r, 'proteger').map(function (s) {
        return '<li>' + AB.icone('seguro') + '<span>' + AB.escapar(s) + '</span></li>';
      }).join('') + '</ul>' +
      '<div class="risco-aqui"><h3>' + AB.escapar(t('riscos.aqui', { lugar: comunidade.nome })) + '</h3>' +
      '<p>' + AB.escapar(texto(r, 'aqui')) + '</p>' +
      '<a class="botao botao-primario" href="' + r.link + '">' + AB.escapar(texto(r, 'link')) + '</a></div>' +
      '</section>';
  }

  /** O que o botão Ouvir de cada risco lê. */
  function falaDoRisco(r) {
    return [texto(r, 'nome') + '.', t('riscos.oQueE') + ': ' + texto(r, 'oQueE'),
      t('riscos.sinais') + ': ' + lista(r, 'sinais').join(' '),
      t('riscos.proteger') + ': ' + lista(r, 'proteger').join(' '),
      t('riscos.aqui', { lugar: comunidade.nome }) + ': ' + texto(r, 'aqui')].join(' ');
  }

  function falaDaPagina() {
    const partes = [t('riscos.titulo') + '. ' + t('riscos.intro')];
    const inmet = AvisosInmet.textoParaOuvir(AvisosInmet.ultima(), true);
    if (inmet) partes.push(inmet);
    RISCOS.forEach(function (r) { partes.push(falaDoRisco(r)); });
    partes.push(t('riscos.nota'));
    return partes.join(' ');
  }

  async function iniciar() {
    comunidade = await AB.iniciarPagina();
    renderizarIndice();
    document.getElementById('lista-riscos').innerHTML = RISCOS.map(cartao).join('');
    document.querySelectorAll('.botao-ouvir-risco').forEach(function (botao) {
      const r = RISCOS.find(function (x) { return x.id === botao.dataset.risco; });
      AB.ligarBotaoDeVoz(botao, function () { return falaDoRisco(r); },
        t('riscos.ouvirEste', { risco: texto(r, 'nome').toLowerCase() }), false);
    });
    AB.configurarOuvir(falaDaPagina);
    const inmet = document.getElementById('avisos-inmet');
    AvisosInmet.renderizar(inmet, await AvisosInmet.carregar());
    AB.aoVoltarConexao(async function () { AvisosInmet.renderizar(inmet, await AvisosInmet.carregar()); });
    // abriu com #risco-SECA no endereço: vai direto para o risco (depois de montar a página)
    if (location.hash) {
      const alvo = document.getElementById(location.hash.slice(1));
      if (alvo) alvo.scrollIntoView();
    }
  }

  iniciar();
})();
