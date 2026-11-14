/* AvisaBrasil: preferências do aparelho (tela Configurações).
   Roda no <head> de todas as telas, antes de desenhar, para não piscar: lê o que está guardado no aparelho
   (localStorage), combina com as preferências do sistema (tema escuro, mais contraste, menos movimento) e
   marca o <html> com atributos data-* que o CSS usa. Nada disso sai do aparelho. */
(function () {
  'use strict';

  const CHAVE = 'avisabrasil.preferencias';

  /** "auto" = segue o aparelho (prefers-color-scheme, prefers-contrast, prefers-reduced-motion). */
  const PADROES = {
    visualizacao: 'auto',   // auto | computador | celular
    tema: 'auto',           // auto | claro | escuro
    texto: 'normal',        // normal | grande | muito-grande
    contraste: 'auto',      // auto | normal | alto
    movimento: 'auto',      // auto | normal | reduzido
    modoSimples: false,     // Início só com o semáforo, o que fazer e o botão de ajuda
    lerAoAbrir: false,      // lê o aviso do Início em voz alta ao abrir
    velocidadeVoz: 'normal', // devagar | normal | rapido
    vlibras: true
  };

  const OPCOES = {
    visualizacao: ['auto', 'computador', 'celular'],
    tema: ['auto', 'claro', 'escuro'],
    texto: ['normal', 'grande', 'muito-grande'],
    contraste: ['auto', 'normal', 'alto'],
    movimento: ['auto', 'normal', 'reduzido'],
    velocidadeVoz: ['devagar', 'normal', 'rapido']
  };

  const VELOCIDADE = { devagar: 0.7, normal: 0.9, rapido: 1.15 };
  const ESCALA_TEXTO = { normal: 1, grande: 1.2, 'muito-grande': 1.4 };
  const LARGURA_COMPUTADOR = 1100;

  function consulta(media) {
    try { return window.matchMedia(media).matches; } catch (e) { return false; }
  }

  /** O que o aparelho pede (usado quando a opção está em "auto"). */
  function sistema() {
    return {
      escuro: consulta('(prefers-color-scheme: dark)'),
      altoContraste: consulta('(prefers-contrast: more)') || consulta('(forced-colors: active)'),
      menosMovimento: consulta('(prefers-reduced-motion: reduce)')
    };
  }

  /** Preferências guardadas, sempre com valores válidos (o que estiver estranho volta ao padrão). */
  function ler() {
    let guardado = {};
    try { guardado = JSON.parse(localStorage.getItem(CHAVE) || '{}') || {}; } catch (e) { guardado = {}; }
    const p = {};
    Object.keys(PADROES).forEach(function (k) {
      const v = guardado[k];
      if (OPCOES[k]) p[k] = OPCOES[k].indexOf(v) >= 0 ? v : PADROES[k];
      else p[k] = typeof v === 'boolean' ? v : PADROES[k];
    });
    return p;
  }

  /** Valores efetivos, já resolvendo o "auto" com o que o aparelho pede. */
  function efetivas(p) {
    const s = sistema();
    return {
      tema: p.tema === 'auto' ? (s.escuro ? 'escuro' : 'claro') : p.tema,
      contraste: p.contraste === 'auto' ? (s.altoContraste ? 'alto' : 'normal') : p.contraste,
      movimento: p.movimento === 'auto' ? (s.menosMovimento ? 'reduzido' : 'normal') : p.movimento,
      visualizacao: p.visualizacao,
      texto: p.texto
    };
  }

  /** Celular: a página inteira vira uma moldura de 390 px. Computador: o celular mostra a versão larga. */
  function ajustarJanela(visualizacao) {
    const meta = document.querySelector('meta[name="viewport"]');
    if (!meta) return;
    meta.setAttribute('content', visualizacao === 'computador' && window.screen && window.screen.width < LARGURA_COMPUTADOR
      ? 'width=' + LARGURA_COMPUTADOR
      : 'width=device-width, initial-scale=1');
  }

  function aplicar() {
    const p = ler();
    const e = efetivas(p);
    const html = document.documentElement;
    html.setAttribute('data-tema', e.tema);
    html.setAttribute('data-contraste', e.contraste);
    html.setAttribute('data-movimento', e.movimento);
    html.setAttribute('data-visualizacao', e.visualizacao);
    html.setAttribute('data-texto', e.texto);
    html.toggleAttribute('data-modo-simples', p.modoSimples);
    html.style.colorScheme = e.tema === 'escuro' ? 'dark' : 'light';
    const corDoTopo = document.querySelector('meta[name="theme-color"]');
    if (corDoTopo) corDoTopo.setAttribute('content', e.contraste === 'alto' ? (e.tema === 'escuro' ? '#000000' : '#002A40') : '#0B4F73');
    ajustarJanela(e.visualizacao);
    try { window.dispatchEvent(new CustomEvent('ab:preferencias', { detail: { preferencias: p, efetivas: e } })); } catch (erro) { /* navegador antigo */ }
  }

  function gravar(parcial) {
    const p = Object.assign(ler(), parcial || {});
    try { localStorage.setItem(CHAVE, JSON.stringify(p)); } catch (e) { /* sem armazenamento: vale só nesta tela */ }
    aplicar();
    return p;
  }

  aplicar();

  // o aparelho mudou (por exemplo, entrou no modo escuro à noite) ou outra aba mudou as preferências
  ['(prefers-color-scheme: dark)', '(prefers-contrast: more)', '(prefers-reduced-motion: reduce)'].forEach(function (media) {
    try { window.matchMedia(media).addEventListener('change', aplicar); } catch (e) { /* navegador antigo */ }
  });
  window.addEventListener('storage', function (e) { if (e.key === CHAVE || e.key === null) aplicar(); });

  window.ABPreferencias = {
    CHAVE: CHAVE,
    PADROES: PADROES,
    OPCOES: OPCOES,
    ler: ler,
    gravar: gravar,
    efetivas: function () { return efetivas(ler()); },
    sistema: sistema,
    aplicar: aplicar,
    velocidadeDaVoz: function () { return VELOCIDADE[ler().velocidadeVoz] || 0.9; },
    escalaDoTexto: function () { return ESCALA_TEXTO[ler().texto] || 1; }
  };
})();
