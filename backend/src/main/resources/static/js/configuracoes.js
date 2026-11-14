/* Tela Configurações: preferências guardadas só neste aparelho (localStorage), aplicadas na hora em
   todas as telas por js/preferencias.js. "Automático" segue o que o aparelho pede. */
(function () {
  'use strict';

  const t = AB.t;
  const P = window.ABPreferencias;
  const IDIOMAS = ['pt', 'es', 'en'];

  /* Opções de cada grupo. "escolha" = botões de escolha única; "interruptor" = liga/desliga. */
  const GRUPOS = [
    {
      id: 'exibicao', titulo: 'config.exibicao', icone: 'tema', itens: [
        { chave: 'visualizacao', tipo: 'escolha', ajuda: true },
        { chave: 'tema', tipo: 'escolha' }
      ]
    },
    {
      id: 'acessibilidade', titulo: 'config.acessibilidade', icone: 'pessoa', itens: [
        { chave: 'texto', tipo: 'escolha' },
        { chave: 'contraste', tipo: 'escolha', ajuda: true },
        { chave: 'modoSimples', tipo: 'interruptor', ajuda: true },
        { chave: 'lerAoAbrir', tipo: 'interruptor', ajuda: true },
        { chave: 'velocidadeVoz', tipo: 'escolha', testarVoz: true },
        { chave: 'movimento', tipo: 'escolha', ajuda: true },
        { chave: 'vlibras', tipo: 'interruptor', ajuda: true }
      ]
    }
  ];

  /** O que o aparelho pede agora, para mostrar ao lado de "Automático". */
  function agoraDoAparelho(chave) {
    const s = P.sistema();
    if (chave === 'tema') return t('config.tema.' + (s.escuro ? 'escuro' : 'claro'));
    if (chave === 'contraste') return t('config.contraste.' + (s.altoContraste ? 'alto' : 'normal'));
    if (chave === 'movimento') return t('config.movimento.' + (s.menosMovimento ? 'reduzido' : 'normal'));
    return '';
  }

  function rotuloDaOpcao(chave, valor) {
    if (valor === 'auto' && chave !== 'visualizacao') return t('config.auto', { agora: agoraDoAparelho(chave) });
    return t('config.' + chave + '.' + valor);
  }

  function campoEscolha(item, p) {
    const nome = 'pref-' + item.chave;
    let html = '<fieldset class="campo-config"><legend>' + AB.escapar(t('config.' + item.chave)) + '</legend>';
    if (item.ajuda) html += '<p class="nota" id="ajuda-' + item.chave + '">' + AB.escapar(t('config.' + item.chave + '.ajuda')) + '</p>';
    html += '<div class="opcoes-config">' + P.OPCOES[item.chave].map(function (valor) {
      return '<label class="opcao-config"><input type="radio" name="' + nome + '" value="' + valor + '"' +
        (p[item.chave] === valor ? ' checked' : '') + (item.ajuda ? ' aria-describedby="ajuda-' + item.chave + '"' : '') +
        '><span data-rotulo="' + item.chave + '|' + valor + '">' + AB.escapar(rotuloDaOpcao(item.chave, valor)) + '</span></label>';
    }).join('') + '</div>';
    if (item.testarVoz) {
      html += '<button type="button" class="botao botao-secundario" id="testar-voz"><span>' + AB.escapar(t('config.testarVoz')) + '</span></button>';
    }
    return html + '</fieldset>';
  }

  function campoInterruptor(item, p) {
    const id = 'pref-' + item.chave;
    return '<div class="campo-config"><label class="interruptor" for="' + id + '">' +
      '<input type="checkbox" role="switch" id="' + id + '" data-chave="' + item.chave + '"' + (p[item.chave] ? ' checked' : '') +
      (item.ajuda ? ' aria-describedby="ajuda-' + item.chave + '"' : '') + '>' +
      '<span class="interruptor-trilho" aria-hidden="true"></span>' +
      '<span class="interruptor-texto"><strong>' + AB.escapar(t('config.' + item.chave)) + '</strong>' +
      (item.ajuda ? '<span class="nota" id="ajuda-' + item.chave + '">' + AB.escapar(t('config.' + item.chave + '.ajuda')) + '</span>' : '') +
      '</span></label></div>';
  }

  function campoIdioma() {
    const atual = AB.idioma();
    return '<section class="cartao secao" aria-labelledby="titulo-idioma"><h2 id="titulo-idioma">' + AB.icone('info') +
      AB.escapar(t('config.idioma')) + '</h2><fieldset class="campo-config" aria-labelledby="titulo-idioma"><p class="nota">' + AB.escapar(t('config.idioma.ajuda')) + '</p><div class="opcoes-config">' +
      IDIOMAS.map(function (codigo) {
        return '<label class="opcao-config" lang="' + codigo + '"><input type="radio" name="pref-idioma" value="' + codigo + '"' +
          (codigo === atual ? ' checked' : '') + '><span>' + AB.escapar(t('idioma.' + codigo)) + '</span></label>';
      }).join('') + '</div></fieldset></section>';
  }

  function renderizar() {
    const p = P.ler();
    document.getElementById('grupos-config').innerHTML = GRUPOS.map(function (g) {
      return '<section class="cartao secao" aria-labelledby="titulo-' + g.id + '"><h2 id="titulo-' + g.id + '">' + AB.icone(g.icone) +
        AB.escapar(t(g.titulo)) + '</h2>' + g.itens.map(function (item) {
          return item.tipo === 'escolha' ? campoEscolha(item, p) : campoInterruptor(item, p);
        }).join('') + '</section>';
    }).join('') + campoIdioma();
  }

  let avisoGuardado;
  function avisarGuardado() {
    const alvo = document.getElementById('estado-config');
    alvo.innerHTML = '<p class="guardado">' + AB.icone('seguro') + '<span>' + AB.escapar(t('config.guardado')) + '</span></p>';
    clearTimeout(avisoGuardado);
    avisoGuardado = setTimeout(function () { alvo.innerHTML = ''; }, 4000);
  }

  /** Atualiza os textos de "Automático (agora: ...)" quando o aparelho muda. */
  function atualizarRotulosAuto() {
    document.querySelectorAll('[data-rotulo]').forEach(function (el) {
      const partes = el.dataset.rotulo.split('|');
      el.textContent = rotuloDaOpcao(partes[0], partes[1]);
    });
  }

  function ligarEventos() {
    document.getElementById('grupos-config').addEventListener('change', function (e) {
      const campo = e.target;
      if (campo.name === 'pref-idioma') {
        try { localStorage.setItem('idioma', campo.value); } catch (erro) { /* sem armazenamento */ }
        if (window.speechSynthesis) window.speechSynthesis.cancel();
        location.replace('/configuracoes.html?idioma=' + encodeURIComponent(campo.value));
        return;
      }
      const mudanca = {};
      if (campo.type === 'radio') mudanca[campo.name.replace('pref-', '')] = campo.value;
      else if (campo.type === 'checkbox') mudanca[campo.dataset.chave] = campo.checked;
      else return;
      P.gravar(mudanca);
      avisarGuardado();
    });
    const testar = document.getElementById('testar-voz');
    if (testar) AB.ligarBotaoDeVoz(testar, function () { return t('config.vozExemplo'); }, t('config.testarVoz'), false);
    window.addEventListener('ab:preferencias', atualizarRotulosAuto);
  }

  /* ---------- Apagar dados deste aparelho ---------- */

  function pedidosEsperando() {
    try { return JSON.parse(localStorage.getItem('avisabrasil.filaAjuda') || '[]').length; } catch (e) { return 0; }
  }

  async function apagarTudo() {
    try { localStorage.clear(); } catch (e) { /* sem armazenamento */ }
    try { sessionStorage.clear(); } catch (e) { /* sem armazenamento */ }
    if (window.caches) {
      const nomes = await caches.keys();
      await Promise.all(nomes.filter(function (n) { return n.indexOf('avisabrasil-') === 0; })
        .map(function (n) { return caches.delete(n); }));
    }
    if (navigator.serviceWorker) {
      // o service worker volta sozinho na próxima tela e baixa de novo o que funciona sem internet
      const registros = await navigator.serviceWorker.getRegistrations();
      await Promise.all(registros.map(function (r) { return r.unregister(); }));
    }
    await fetch('/api/painel/sair', { method: 'POST' }).catch(function () { return null; });
  }

  function ligarApagar() {
    document.getElementById('lista-apagar').innerHTML = AB.lista('config.apagar.itens').map(function (item) {
      return '<li>' + AB.icone('lixeira') + '<span>' + AB.escapar(item) + '</span></li>';
    }).join('');
    const botao = document.getElementById('botao-apagar');
    botao.insertAdjacentHTML('afterbegin', AB.icone('lixeira'));
    botao.addEventListener('click', async function () {
      // dois toques, sem janela de confirmação do navegador
      if (botao.dataset.confirmar !== 'true') {
        botao.dataset.confirmar = 'true';
        botao.querySelector('span').textContent = t('config.apagar.confirmar');
        const n = pedidosEsperando();
        const aviso = document.getElementById('aviso-fila');
        aviso.hidden = n === 0;
        if (n) aviso.innerHTML = AB.icone('alerta') + '<span>' + AB.escapar(t('config.apagar.filaPendente', { n: n })) + '</span>';
        return;
      }
      botao.disabled = true;
      await apagarTudo();
      location.replace('/configuracoes.html?apagado=1');
    });
    if (new URLSearchParams(location.search).get('apagado')) {
      document.getElementById('resultado-apagar').innerHTML = '<p class="guardado">' + AB.icone('seguro') +
        '<span>' + AB.escapar(t('config.apagar.feito')) + '</span></p>';
    }
  }

  /** O que o botão Ouvir lê: cada opção e o valor escolhido. */
  function falaDaPagina() {
    const p = P.ler();
    const partes = [t('config.titulo') + '. ' + t('config.intro')];
    GRUPOS.forEach(function (g) {
      partes.push(t(g.titulo) + '.');
      g.itens.forEach(function (item) {
        const valor = item.tipo === 'escolha' ? rotuloDaOpcao(item.chave, p[item.chave])
          : t(p[item.chave] ? 'config.ligado' : 'config.desligado');
        partes.push(t('config.ouvir.item', { nome: t('config.' + item.chave), valor: valor }));
      });
    });
    partes.push(t('config.ouvir.item', { nome: t('config.idioma'), valor: t('idioma.' + AB.idioma()) }));
    partes.push(t('config.apagar.titulo') + '. ' + t('config.apagar.explica') + ' ' + AB.lista('config.apagar.itens').join('. ') + '.');
    return partes.join(' ');
  }

  async function iniciar() {
    await AB.iniciarPagina();
    renderizar();
    ligarEventos();
    ligarApagar();
    AB.configurarOuvir(falaDaPagina);
  }

  iniciar();
})();
