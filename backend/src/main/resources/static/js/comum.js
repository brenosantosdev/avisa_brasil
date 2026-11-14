/* AvisaBrasil: funções comuns a todas as telas.
   Textos em i18n/<idioma>.json; horários sempre no fuso da comunidade (America/Manaus). */
(function () {
  'use strict';

  const FUSO = 'America/Manaus';
  /* Para acrescentar um idioma (por exemplo, uma língua indígena com tradução validada por falantes
     nativos): criar i18n/<código>.json com as mesmas chaves do pt.json, pôr o código aqui e o nome
     do idioma na chave "idioma.<código>" de cada arquivo. */
  const IDIOMAS_DISPONIVEIS = ['pt', 'es', 'en'];

  let textos = {};
  let localidade = 'pt-BR';
  let idiomaAtual = 'pt';

  /* ---------- Textos (i18n) ---------- */

  function idiomaEscolhido() {
    let idioma = 'pt';
    try { idioma = localStorage.getItem('idioma') || 'pt'; } catch (e) { /* sem localStorage */ }
    // ?idioma=es no endereço também vale (e fica guardado), útil para mandar o link já no idioma certo
    const naUrl = new URLSearchParams(location.search).get('idioma');
    if (naUrl && IDIOMAS_DISPONIVEIS.includes(naUrl)) {
      idioma = naUrl;
      try { localStorage.setItem('idioma', naUrl); } catch (e) { /* sem localStorage */ }
    }
    return IDIOMAS_DISPONIVEIS.includes(idioma) ? idioma : 'pt';
  }

  async function carregarTextos() {
    const idioma = idiomaEscolhido();
    const resposta = await fetch('/i18n/' + idioma + '.json');
    textos = await resposta.json();
    idiomaAtual = idioma;
    localidade = textos._idioma || 'pt-BR';
    document.documentElement.lang = localidade;
  }

  /** Texto traduzido, trocando {variaveis}. */
  function t(chave, vars) {
    let texto = textos[chave];
    if (texto === undefined) return chave;
    if (vars) {
      Object.keys(vars).forEach(function (nome) {
        texto = texto.split('{' + nome + '}').join(vars[nome]);
      });
    }
    return texto;
  }

  /** Lista traduzida (por exemplo, o checklist de cada nível). */
  function lista(chave) {
    const valor = textos[chave];
    return Array.isArray(valor) ? valor : [];
  }

  function aplicarTextos(raiz) {
    (raiz || document).querySelectorAll('[data-i18n]').forEach(function (el) {
      el.textContent = t(el.dataset.i18n);
    });
    (raiz || document).querySelectorAll('[data-i18n-aria]').forEach(function (el) {
      el.setAttribute('aria-label', t(el.dataset.i18nAria));
    });
  }

  /* ---------- Números ---------- */

  function numero(valor, casas) {
    if (valor === null || valor === undefined) return '–';
    return new Intl.NumberFormat(localidade, {
      minimumFractionDigits: casas || 0,
      maximumFractionDigits: casas || 0
    }).format(valor);
  }

  /** 194921 → "195 mil" */
  function numeroCompacto(valor) {
    return new Intl.NumberFormat(localidade, { notation: 'compact', maximumFractionDigits: 0 }).format(valor);
  }

  /** Explica milímetros de chuva em baldes de 20 litros por metro quadrado. */
  function textoBaldes(mm) {
    const baldes = mm / 20;
    if (mm < 1) return t('baldes.quaseNada');
    if (baldes < 0.5) return t('baldes.menosMeio');
    if (baldes < 0.9) return t('baldes.quaseUm');
    if (baldes < 1.25) return t('baldes.um');
    const inteiro = Math.floor(baldes);
    if (baldes - inteiro < 0.15) return t('baldes.cerca', { n: inteiro });
    return t('baldes.mais', { n: inteiro });
  }

  /* ---------- Datas em linguagem natural ---------- */

  /** Data de hoje (AAAA-MM-DD) no fuso da comunidade. */
  function hojeNaComunidade() {
    return new Intl.DateTimeFormat('en-CA', { timeZone: FUSO }).format(new Date());
  }

  function somarDias(dataIso, dias) {
    const d = new Date(dataIso + 'T12:00:00Z');
    d.setUTCDate(d.getUTCDate() + dias);
    return d.toISOString().slice(0, 10);
  }

  function maiuscula(texto) {
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  /** "Hoje", "Amanhã" ou o dia da semana ("Sábado"). */
  function nomeDoDia(dataIso) {
    const hoje = hojeNaComunidade();
    if (dataIso === hoje) return t('dia.hoje');
    if (dataIso === somarDias(hoje, 1)) return t('dia.amanha');
    const d = new Date(dataIso + 'T12:00:00Z');
    return maiuscula(new Intl.DateTimeFormat(localidade, { weekday: 'long', timeZone: 'UTC' }).format(d));
  }

  /** "14/11" */
  function dataCurta(dataIso) {
    const d = new Date(dataIso + 'T12:00:00Z');
    return new Intl.DateTimeFormat(localidade, { day: '2-digit', month: '2-digit', timeZone: 'UTC' }).format(d);
  }

  /** "dez. de 2019" */
  function mesEAno(dataIso) {
    const d = new Date(dataIso + 'T12:00:00Z');
    return new Intl.DateTimeFormat(localidade, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(d);
  }

  /** 236 → "236 m"; 1063 → "1,1 km" */
  function distancia(metros) {
    if (metros < 1000) return t('distancia.m', { n: numero(Math.round(metros / 10) * 10) });
    return t('distancia.km', { n: numero(metros / 1000, 1) });
  }

  /** Explica centímetros com partes do corpo: 40 → "mais ou menos a altura de um joelho". */
  function comparacaoAltura(cm) {
    if (cm <= 20) return t('altura.palmo');
    if (cm <= 60) return t('altura.joelho');
    if (cm <= 110) return t('altura.cintura');
    if (cm <= 190) return t('altura.pessoa');
    return t('altura.pessoas', { n: numero(cm / 160, 1) });
  }

  /** "19:45" no fuso da comunidade. */
  function hora(dataHoraIso) {
    return new Intl.DateTimeFormat(localidade, { hour: '2-digit', minute: '2-digit', timeZone: FUSO }).format(new Date(dataHoraIso));
  }

  /** "hoje às 19:45", "ontem às 08:00", "em 09/11 às 14:00" (no fuso da comunidade). */
  function quando(dataHoraIso) {
    const momento = new Date(dataHoraIso);
    const dia = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO }).format(momento);
    const hora = new Intl.DateTimeFormat(localidade, { hour: '2-digit', minute: '2-digit', timeZone: FUSO }).format(momento);
    const hoje = hojeNaComunidade();
    if (dia === hoje) return t('quando.hoje', { hora: hora });
    if (dia === somarDias(hoje, -1)) return t('quando.ontem', { hora: hora });
    return t('quando.data', { data: dataCurta(dia), hora: hora });
  }

  /* ---------- Ícones (SVG, cor herdada do texto) ---------- */

  const caminhos = {
    rio: '<path d="M3 8c2.5-2 4.5-2 7 0s4.5 2 7 0 3-1.5 4-1"/><path d="M3 13c2.5-2 4.5-2 7 0s4.5 2 7 0 3-1.5 4-1"/><path d="M3 18c2.5-2 4.5-2 7 0s4.5 2 7 0 3-1.5 4-1"/>',
    som: '<path d="M4 9v6h4l5 4V5L8 9H4z"/><path d="M16.5 8.5a5 5 0 0 1 0 7"/><path d="M19 6a8.5 8.5 0 0 1 0 12"/>',
    parar: '<rect x="6" y="6" width="12" height="12" rx="1.5"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    nuvem: '<path d="M7 18h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.1 11.2 3.5 3.5 0 0 0 7 18z"/>',
    chuva: '<path d="M7 14h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.1 7.2 3.5 3.5 0 0 0 7 14z"/><path d="M8 17l-1 3M12 17l-1 3M16 17l-1 3"/>',
    trovoada: '<path d="M7 13h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.1 6.2 3.5 3.5 0 0 0 7 13z"/><path d="M12.5 13l-2 4h3l-2 4"/>',
    neblina: '<path d="M4 9h16M3 13h18M5 17h14"/>',
    gota: '<path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z"/>',
    termometro: '<path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0z"/><path d="M12 9v7"/>',
    sobe: '<path d="M12 20V5M5 11l7-7 7 7"/>',
    desce: '<path d="M12 4v15M5 13l7 7 7-7"/>',
    estavel: '<path d="M4 12h14M13 6l6 6-6 6"/>',
    alerta: '<path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18v.5"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/>',
    regua: '<path d="M4 20V4h6v16"/><path d="M4 8h3M4 12h4M4 16h3"/><path d="M14 18c1.5-1.2 3-1.2 4.5 0s2.5 1.2 3.5 0"/>',
    escola: '<path d="M2 9l10-5 10 5-10 5L2 9z"/><path d="M6 11v5c3 2 9 2 12 0v-5"/>',
    saude: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M12 7v10M7 12h10"/>',
    predio: '<path d="M3 21h18M5 21V10M19 21V10M9 21v-6h6v6"/><path d="M2 10l10-6 10 6"/>',
    abrigo: '<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-5h4v5"/>',
    seguro: '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
    local: '<path d="M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z"/><circle cx="12" cy="9" r="2.5"/>',
    pessoa: '<circle cx="12" cy="6" r="3"/><path d="M12 10v6M8 21l4-5 4 5M7 13h10"/>',
    lixeira: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
    camadas: '<path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/>'
  };

  function icone(nome, titulo) {
    const rotulo = titulo ? ' role="img" aria-label="' + escapar(titulo) + '"' : ' aria-hidden="true"';
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
      'stroke-linecap="round" stroke-linejoin="round"' + rotulo + '>' + (caminhos[nome] || '') + '</svg>';
  }

  function escapar(texto) {
    return String(texto)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /* ---------- Risco (motor) ---------- */

  const ICONE_DO_NIVEL = { NORMAL: 'seguro', ATENCAO: 'info', ALERTA: 'alerta', EMERGENCIA: 'alerta' };

  /** cm na régua → "7,74 m" */
  function metros(cm) {
    return t('ra.metros', { n: numero(cm / 100, 2) });
  }

  /** 30 → "30 horas"; 100 → "4,2 dias" */
  function duracao(horas) {
    return horas < 48 ? t('tempo.horas', { n: numero(horas) }) : t('tempo.dias', { n: numero(horas / 24, 1) });
  }

  /** Etiqueta do nível: ícone + cor + palavra (e o tipo, se houver). */
  function etiquetaNivel(nivel, tipo, classeExtra) {
    return '<span class="etiqueta nivel-' + nivel + (classeExtra ? ' ' + classeExtra : '') + '">' + icone(ICONE_DO_NIVEL[nivel]) +
      escapar(t('nivel.' + nivel) + (tipo ? ' · ' + t('tipoRisco.' + tipo) : '')) + '</span>';
  }

  /** Explica um fator do motor de risco em linguagem simples. */
  function textoFator(f) {
    const d = f.dados || {};
    const fonte = f.fonte === 'SENSOR' && f.simulado ? t('fonteRisco.SENSOR.simulado') : t('fonteRisco.' + f.fonte);
    return t('motivo.' + f.motivo, {
      fonte: fonte,
      nivel: t('nivel.' + f.nivel),
      cota: d.cotaCm !== undefined ? metros(d.cotaCm) : '',
      limite: d.limiteCm !== undefined ? metros(d.limiteCm) : '',
      percentual: d.percentual,
      ini: d.anoInicial,
      fim: d.anoFinal,
      tempo: d.horas !== undefined ? duracao(d.horas) : '',
      mm: d.mm !== undefined ? numero(d.mm, 1) : '',
      dia: d.data ? nomeDoDia(d.data).toLowerCase() : '',
      variacao: d.variacao !== undefined ? numero(d.variacao, 1) : ''
    });
  }

  /* ---------- Fonte e situação dos dados ---------- */

  /** Aviso no topo da seção quando o dado está desatualizado ou indisponível. */
  function avisoSituacao(resposta) {
    if (!resposta || resposta.situacao === 'ATUALIZADO') return '';
    const situacao = resposta.situacao || 'INDISPONIVEL';
    const texto = t('situacao.' + situacao, { quando: resposta.atualizadoEm ? quando(resposta.atualizadoEm) : '' });
    return '<div class="aviso-situacao aviso-' + situacao + '" role="status">' + icone('alerta') +
      '<span>' + escapar(texto) + '</span></div>';
  }

  /** Rodapé da seção: fonte com link e horário da última atualização. */
  function rodapeFonte(resposta, extra) {
    const fonte = '<p>' + escapar(t('fonte.de', { fonte: '' })) +
      '<a href="' + escapar(resposta.fonteUrl) + '" target="_blank" rel="noopener">' + escapar(resposta.fonte) + '</a></p>';
    const atualizado = resposta.atualizadoEm
      ? '<p>' + escapar(t('fonte.atualizado', { quando: quando(resposta.atualizadoEm) })) + '</p>'
      : '<p>' + escapar(t('fonte.nunca')) + '</p>';
    return '<div class="fonte">' + fonte + atualizado + (extra || '') + '</div>';
  }

  /* ---------- Botão "Ouvir" (Web Speech API) ---------- */

  function configurarOuvir(gerarTexto) {
    const botao = document.getElementById('botao-ouvir');
    if (!botao) return;
    const rotulo = botao.querySelector('span');
    const voz = window.speechSynthesis;

    function mostrar(falando) {
      botao.setAttribute('aria-pressed', falando ? 'true' : 'false');
      botao.querySelector('svg').outerHTML = icone(falando ? 'parar' : 'som');
      rotulo.textContent = t(falando ? 'ouvir.parar' : 'ouvir');
    }

    mostrar(false);
    if (!voz) {
      botao.disabled = true;
      botao.title = t('ouvir.indisponivel');
      return;
    }

    botao.addEventListener('click', function () {
      if (voz.speaking) {
        voz.cancel();
        mostrar(false);
        return;
      }
      const fala = new SpeechSynthesisUtterance(gerarTexto());
      fala.lang = localidade;
      fala.rate = 0.9;
      fala.onend = function () { mostrar(false); };
      fala.onerror = function () { mostrar(false); };
      voz.speak(fala);
      mostrar(true);
    });
  }

  /* ---------- Topo comum ---------- */

  const PAGINAS = [
    { id: 'inicio', href: '/', texto: 'nav.inicio' },
    { id: 'rio-agora', href: '/rio-agora.html', texto: 'nav.rioAgora' },
    { id: 'proximos-dias', href: '/proximos-dias.html', texto: 'nav.proximosDias' },
    { id: 'mapa', href: '/mapa.html', texto: 'nav.mapa' },
    { id: 'ajuda', href: '/ajuda.html', texto: 'nav.ajuda' },
    { id: 'painel', href: '/painel.html', texto: 'nav.painel' }
  ];

  /** Título da aba do navegador no idioma escolhido. */
  function traduzirTitulo(atual) {
    const pagina = PAGINAS.find(function (p) { return p.id === atual; });
    const chave = atual === 'painel' ? 'painel.titulo' : (pagina ? pagina.texto : null);
    document.title = !chave || atual === 'inicio' ? t('app.nome') : t(chave) + ' · ' + t('app.nome');
  }

  /** Seletor de idioma no topo, ao lado do botão Ouvir. A escolha fica guardada no aparelho. */
  function montarSeletorIdioma() {
    const topo = document.querySelector('.topo-interno');
    if (!topo || IDIOMAS_DISPONIVEIS.length < 2) return;
    const caixa = document.createElement('label');
    caixa.className = 'seletor-idioma';
    caixa.innerHTML = '<span class="so-leitor">' + escapar(t('idioma.rotulo')) + '</span>' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"/></svg>' +
      '<select id="seletor-idioma">' + IDIOMAS_DISPONIVEIS.map(function (codigo) {
        return '<option value="' + codigo + '" lang="' + codigo + '"' + (codigo === idiomaAtual ? ' selected' : '') + '>' +
          escapar(t('idioma.' + codigo)) + '</option>';
      }).join('') + '</select>';
    topo.insertBefore(caixa, document.getElementById('botao-ouvir'));
    caixa.querySelector('select').addEventListener('change', function (e) {
      try { localStorage.setItem('idioma', e.target.value); } catch (erro) { /* sem localStorage: vale só agora */ }
      if (window.speechSynthesis) window.speechSynthesis.cancel();
      // recarrega para que todos os textos, números e datas mudem juntos
      const url = new URL(location.href);
      url.searchParams.set('idioma', e.target.value);
      location.replace(url.toString());
    });
  }

  /** Widget VLibras (gov.br): traduz o texto da página para Libras. */
  function carregarVLibras() {
    if (document.querySelector('[vw]')) return;
    const widget = document.createElement('div');
    widget.setAttribute('vw', '');
    widget.className = 'enabled';
    widget.innerHTML = '<div vw-access-button class="active"></div>' +
      '<div vw-plugin-wrapper><div class="vw-plugin-top-wrapper"></div></div>';
    document.body.appendChild(widget);
    const script = document.createElement('script');
    script.src = 'https://vlibras.gov.br/app/vlibras-plugin.js';
    script.onload = function () {
      try {
        new window.VLibras.Widget('https://vlibras.gov.br/app');
        // o VLibras inicia no evento load da página, que pode já ter passado
        if (document.readyState === 'complete' && typeof window.onload === 'function') window.onload();
      } catch (e) { /* VLibras fora do ar: o resto da página continua funcionando */ }
    };
    document.body.appendChild(script);
  }

  /** Monta o menu em <nav id="navegacao" data-atual="..."> marcando a página atual. */
  function montarNavegacao() {
    const nav = document.getElementById('navegacao');
    if (!nav) return;
    nav.setAttribute('aria-label', t('nav.principal'));
    nav.innerHTML = '<ul>' + PAGINAS.map(function (p) {
      const atual = p.id === nav.dataset.atual ? ' aria-current="page"' : '';
      return '<li><a href="' + p.href + '"' + atual + '>' + escapar(t(p.texto)) + '</a></li>';
    }).join('') + '</ul>';
  }

  async function iniciarPagina() {
    await carregarTextos();
    aplicarTextos();
    montarNavegacao();
    const nav = document.getElementById('navegacao');
    traduzirTitulo(nav ? nav.dataset.atual : null);
    montarSeletorIdioma();
    carregarVLibras();
    try {
      const comunidade = await (await fetch('/api/comunidade')).json();
      document.querySelectorAll('[data-comunidade-local]').forEach(function (el) {
        el.textContent = t('app.local', { lugar: comunidade.nome, uf: comunidade.uf, rio: comunidade.rio });
      });
      return comunidade;
    } catch (e) {
      return { nome: '', uf: '', rio: '' };
    }
  }

  window.AB = {
    t: t,
    lista: lista,
    metros: metros,
    duracao: duracao,
    etiquetaNivel: etiquetaNivel,
    textoFator: textoFator,
    numero: numero,
    numeroCompacto: numeroCompacto,
    distancia: distancia,
    comparacaoAltura: comparacaoAltura,
    textoBaldes: textoBaldes,
    hojeNaComunidade: hojeNaComunidade,
    nomeDoDia: nomeDoDia,
    dataCurta: dataCurta,
    mesEAno: mesEAno,
    quando: quando,
    hora: hora,
    idioma: function () { return idiomaAtual; },
    icone: icone,
    escapar: escapar,
    avisoSituacao: avisoSituacao,
    rodapeFonte: rodapeFonte,
    configurarOuvir: configurarOuvir,
    iniciarPagina: iniciarPagina
  };
})();
