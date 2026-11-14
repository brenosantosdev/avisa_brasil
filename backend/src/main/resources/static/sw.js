/* AvisaBrasil: service worker (PWA).
   - Telas que funcionam sem internet: Início, Preciso de ajuda e Entenda os riscos.
   - Sempre tenta a rede primeiro; se não houver conexão, usa o que ficou salvo neste aparelho.
   - Dados: só os públicos da lista DADOS_SEM_CONEXAO. Nada do painel da Defesa Civil nem pedidos de ajuda
     de outras pessoas é guardado aqui.
   - Quando a resposta vem do que ficou salvo, ela leva o cabeçalho X-Sem-Conexao, e X-Salvo-Em diz quando
     o dado foi salvo, para a tela mostrar o aviso "sem conexão" com o horário. */
'use strict';

const VERSAO = 'v2'; // mude quando a lista abaixo mudar, para baixar tudo de novo
const CACHE_TELAS = 'avisabrasil-telas-' + VERSAO;
const CACHE_DADOS = 'avisabrasil-dados'; // não muda com a versão: guarda o último dado válido
const CACHE_FONTES = 'avisabrasil-fontes';

/** Telas abertas sem internet. Para acrescentar uma, ponha aqui o endereço e os arquivos dela em ARQUIVOS. */
const TELAS_SEM_CONEXAO = ['/', '/ajuda.html', '/riscos.html', '/offline.html'];

const ARQUIVOS = TELAS_SEM_CONEXAO.concat([
  '/css/estilo.css', '/css/inicio.css', '/css/ajuda.css', '/css/riscos.css',
  '/js/comum.js', '/js/inicio.js', '/js/ajuda.js', '/js/riscos.js', '/js/avisos-inmet.js',
  '/i18n/pt.json', '/i18n/es.json', '/i18n/en.json',
  '/manifest.webmanifest', '/icones/icone.svg', '/icones/icone-192.png'
]);

/** Dados públicos guardados para uso sem conexão (o caminho exato, sem parâmetros). */
const DADOS_SEM_CONEXAO = ['/api/comunidade', '/api/risco', '/api/avisos-inmet', '/api/pedidos-ajuda/privacidade'];

const FONTE_DO_TEXTO = 'https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap';

/** Rede lenta: depois deste tempo, usa o que está salvo (e a resposta da rede, se chegar, ainda atualiza o salvo). */
const ESPERA_DA_REDE_MS = 6000;

self.addEventListener('install', function (evento) {
  evento.waitUntil((async function () {
    const cache = await caches.open(CACHE_TELAS);
    await cache.addAll(ARQUIVOS.map(function (url) { return new Request(url, { cache: 'reload' }); }));
    await guardarFonte();
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', function (evento) {
  evento.waitUntil((async function () {
    const nomes = await caches.keys();
    await Promise.all(nomes.filter(function (n) { return n.startsWith('avisabrasil-telas-') && n !== CACHE_TELAS; })
      .map(function (n) { return caches.delete(n); }));
    await self.clients.claim();
  })());
});

/** A fonte Atkinson Hyperlegible também fica salva (sem ela, o celular usa a fonte dele). */
async function guardarFonte() {
  try {
    const cache = await caches.open(CACHE_FONTES);
    const css = await fetch(FONTE_DO_TEXTO);
    if (!css.ok) return;
    const texto = await css.clone().text();
    await cache.put(FONTE_DO_TEXTO, css);
    const arquivos = texto.match(/https:\/\/fonts\.gstatic\.com\/[^)'"]+/g) || [];
    await Promise.all(arquivos.map(function (url) {
      return fetch(url).then(function (r) { if (r.ok) return cache.put(url, r); });
    }));
  } catch (e) {
    // sem a fonte salva, o resto funciona igual
  }
}

self.addEventListener('fetch', function (evento) {
  const pedido = evento.request;
  if (pedido.method !== 'GET') return; // envios (como o pedido de ajuda) vão direto; a fila fica na tela
  const url = new URL(pedido.url);

  if (url.origin === self.location.origin) {
    if (url.pathname.startsWith('/api/')) {
      if (DADOS_SEM_CONEXAO.includes(url.pathname)) evento.respondWith(dadoComReserva(pedido, url.pathname));
      return; // outros dados (painel, eventos ao vivo, listas) nunca são guardados
    }
    if (pedido.mode === 'navigate') {
      evento.respondWith(tela(pedido, url));
      return;
    }
    if (/^\/(css|js|i18n|icones)\/|^\/manifest\.webmanifest$/.test(url.pathname)) {
      evento.respondWith(redePrimeiro(pedido, CACHE_TELAS, url.pathname));
    }
    return;
  }

  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    evento.respondWith(fonte(pedido));
  }
  // mapas, gráficos, VLibras e outros serviços externos: só com internet
});

function comLimiteDeTempo(promessa, ms) {
  return new Promise(function (resolver, rejeitar) {
    const relogio = setTimeout(function () { rejeitar(new Error('rede lenta')); }, ms);
    promessa.then(function (r) { clearTimeout(relogio); resolver(r); },
      function (e) { clearTimeout(relogio); rejeitar(e); });
  });
}

/** Rede primeiro; sem conexão, o que está salvo. Guarda só respostas boas. */
async function redePrimeiro(pedido, nomeCache, chave) {
  const cache = await caches.open(nomeCache);
  const daRede = fetch(pedido).then(function (resposta) {
    if (resposta.ok && resposta.type === 'basic') cache.put(chave, resposta.clone());
    return resposta;
  });
  daRede.catch(function () { /* sem conexão: tratado abaixo */ });
  try {
    return await comLimiteDeTempo(daRede, ESPERA_DA_REDE_MS);
  } catch (e) {
    const salva = await cache.match(chave);
    if (salva) return salva;
    return daRede; // nada salvo: espera a rede de qualquer jeito
  }
}

/** Telas: as da lista abrem sem internet; as outras mostram a página "sem conexão". */
async function tela(pedido, url) {
  const caminho = url.pathname === '/index.html' ? '/' : url.pathname;
  if (TELAS_SEM_CONEXAO.includes(caminho)) {
    return redePrimeiro(pedido, CACHE_TELAS, caminho);
  }
  try {
    return await fetch(pedido);
  } catch (e) {
    const offline = await caches.match('/offline.html', { cacheName: CACHE_TELAS });
    return offline || Response.error();
  }
}

/** Dado público: rede primeiro; sem conexão, o último dado salvo com o aviso no cabeçalho. */
async function dadoComReserva(pedido, chave) {
  const cache = await caches.open(CACHE_DADOS);
  const daRede = fetch(pedido).then(async function (resposta) {
    if (resposta.ok) {
      const cabecalhos = new Headers(resposta.headers);
      cabecalhos.set('X-Salvo-Em', new Date().toISOString());
      await cache.put(chave, new Response(await resposta.clone().arrayBuffer(), { status: 200, headers: cabecalhos }));
    }
    return resposta;
  });
  daRede.catch(function () { /* sem conexão: tratado abaixo */ });
  try {
    return await comLimiteDeTempo(daRede, ESPERA_DA_REDE_MS);
  } catch (e) {
    const salva = await cache.match(chave);
    if (!salva) {
      return new Response(JSON.stringify({ semConexao: true }), {
        status: 503, headers: { 'Content-Type': 'application/json', 'X-Sem-Conexao': '1' }
      });
    }
    const cabecalhos = new Headers(salva.headers);
    cabecalhos.set('X-Sem-Conexao', '1');
    return new Response(await salva.arrayBuffer(), { status: 200, headers: cabecalhos });
  }
}

/** Fonte do texto: o que está salvo primeiro (a fonte não muda). */
async function fonte(pedido) {
  const cache = await caches.open(CACHE_FONTES);
  const salva = await cache.match(pedido.url, { ignoreVary: true });
  if (salva) return salva;
  try {
    const resposta = await fetch(pedido);
    if (resposta.ok || resposta.type === 'opaque') cache.put(pedido.url, resposta.clone());
    return resposta;
  } catch (e) {
    return Response.error();
  }
}
