/* Painel da Defesa Civil: entrada com senha, situação ao vivo (SSE), pedidos de ajuda, sensor,
   alerta manual, envio de alertas e pontos seguros. */
(function () {
  'use strict';

  const AB = window.AB;
  const ABMapa = window.ABMapa;
  const t = AB.t;
  const TIPOS = ['ABRIGO', 'ESCOLA', 'SAUDE', 'APOIO'];
  const NIVEIS = ['ATENCAO', 'ALERTA', 'EMERGENCIA', 'NORMAL'];

  const estado = {
    comunidade: null, mapa: null, camada: null, escolhido: null, pontos: [],
    mapaPedidos: null, camadaPedidos: null, pedidos: [], idsConhecidos: null,
    grafico: null, risco: null, eventos: null, iniciado: false
  };

  const campo = function (nome) { return document.getElementById('campo-' + nome); };

  async function json(url, opcoes) {
    const resposta = await fetch(url, Object.assign({ cache: 'no-store' }, opcoes || {}));
    if (resposta.status === 401) {
      mostrarLogin();
      throw new Error('HTTP 401');
    }
    if (!resposta.ok) throw new Error('HTTP ' + resposta.status);
    return resposta.json();
  }

  function enviarJson(url, metodo, corpo) {
    return json(url, { method: metodo, headers: { 'Content-Type': 'application/json' }, body: corpo ? JSON.stringify(corpo) : undefined });
  }

  /* ---------- Entrada (senha) ---------- */

  function mostrarLogin(erro) {
    if (estado.eventos) { estado.eventos.close(); estado.eventos = null; }
    document.getElementById('area-painel').hidden = true;
    document.getElementById('area-login').hidden = false;
    document.getElementById('mensagem-login').innerHTML = erro
      ? '<p class="mensagem-erro">' + AB.escapar(t('painel.entrar.' + erro)) + '</p>' : '';
    document.getElementById('senha').focus();
  }

  async function entrar(e) {
    e.preventDefault();
    const senha = document.getElementById('senha').value;
    const resposta = await fetch('/api/painel/entrar', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ senha: senha })
    }).catch(function () { return null; });
    document.getElementById('senha').value = '';
    if (resposta && resposta.ok) {
      abrirPainel();
      return;
    }
    const corpo = resposta ? await resposta.json().catch(function () { return {}; }) : {};
    mostrarLogin(corpo.erro || 'SENHA_ERRADA');
  }

  async function sair() {
    await fetch('/api/painel/sair', { method: 'POST' }).catch(function () { return null; });
    mostrarLogin();
  }

  /* ---------- Situação agora ---------- */

  function renderizarSituacao(r) {
    estado.risco = r;
    const alvo = document.getElementById('situacao');
    if (!r) { alvo.innerHTML = ''; return; }
    let html = '<p>' + AB.etiquetaNivel(r.nivel, r.tipo, 'etiqueta-grande') +
      (r.simulado ? '<span class="marca-simulacao">' + AB.escapar(t('painel.simulado')) + '</span>' : '') + '</p>';
    html += '<p><strong>' + AB.escapar(r.principal ? AB.textoFator(r.principal) : t('inicio.semRisco')) + '</strong></p>';
    const outros = r.fatores.filter(function (f) { return JSON.stringify(f) !== JSON.stringify(r.principal); });
    if (outros.length) {
      html += '<ul class="lista-canais">' + outros.map(function (f) {
        return '<li>' + AB.etiquetaNivel(f.nivel, f.tipo) + '<span>' + AB.escapar(AB.textoFator(f)) + '</span></li>';
      }).join('') + '</ul>';
    }
    html += '<p class="nota">' + AB.escapar(t('inicio.avaliado', { quando: AB.quando(r.avaliadoEm) })) + '</p>';
    alvo.innerHTML = html;
    renderizarSensor();
  }

  /* ---------- Pedidos de ajuda (lista + mapa) ---------- */

  function marcadorPedido(p) {
    const ajuda = p.tipo === 'PRECISO_DE_AJUDA';
    return ABMapa.marcadorSimples(p.latitude, p.longitude, ajuda ? 'marcador-pedido' : 'marcador-bem',
      ajuda ? 'alerta' : 'seguro', t('painel.resposta.' + p.tipo));
  }

  /** Som curto quando chega um pedido de ajuda novo (o painel também mostra em vermelho). */
  function tocarAviso() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      [0, 0.25].forEach(function (inicio) {
        const osc = ctx.createOscillator();
        const vol = ctx.createGain();
        osc.frequency.value = 880;
        vol.gain.value = 0.15;
        osc.connect(vol).connect(ctx.destination);
        osc.start(ctx.currentTime + inicio);
        osc.stop(ctx.currentTime + inicio + 0.15);
      });
    } catch (e) { /* sem áudio */ }
  }

  async function carregarPedidos() {
    const lista = await json('/api/pedidos-ajuda').catch(function () { return null; });
    if (!lista) return;
    // pedidos de ajuda novos tocam um aviso (não na primeira carga)
    const novos = lista.filter(function (p) {
      return estado.idsConhecidos && !estado.idsConhecidos.has(p.id) && p.tipo === 'PRECISO_DE_AJUDA';
    });
    if (novos.length) tocarAviso();
    estado.idsConhecidos = new Set(lista.map(function (p) { return p.id; }));
    estado.pedidos = lista;

    // abertos primeiro
    const ordenados = lista.slice().sort(function (a, b) {
      const pa = a.tipo === 'PRECISO_DE_AJUDA' && !a.atendido ? 0 : 1;
      const pb = b.tipo === 'PRECISO_DE_AJUDA' && !b.atendido ? 0 : 1;
      return pa - pb || b.id - a.id;
    });
    const abertos = lista.filter(function (p) { return p.tipo === 'PRECISO_DE_AJUDA' && !p.atendido; }).length;
    const alvo = document.getElementById('pedidos');
    if (!lista.length) {
      alvo.innerHTML = '<p>' + AB.escapar(t('painel.pedidos.vazio')) + '</p>';
    } else {
      alvo.innerHTML = '<p><strong>' + AB.escapar(t('painel.pedidos.abertos', { n: abertos })) + '</strong></p>' +
        '<ul class="lista-pontos">' + ordenados.slice(0, 30).map(itemPedido).join('') + '</ul>';
    }

    if (estado.camadaPedidos) estado.camadaPedidos.remove();
    estado.camadaPedidos = L.layerGroup().addTo(estado.mapaPedidos);
    lista.filter(function (p) { return p.latitude !== null && !p.atendido; }).forEach(function (p) {
      marcadorPedido(p).bindPopup('<h3>' + AB.escapar(t('painel.resposta.' + p.tipo)) + '</h3><p>' +
        AB.escapar([p.nome, p.pessoas ? t('painel.resposta.pessoas', { n: p.pessoas }) : null, AB.quando(p.criadoEm)]
          .filter(Boolean).join(' · ')) + '</p>').addTo(estado.camadaPedidos);
    });
  }

  function itemPedido(p) {
    const ajuda = p.tipo === 'PRECISO_DE_AJUDA';
    const detalhes = [AB.quando(p.criadoEm), t('painel.pedidos.canal.' + p.canal)];
    if (p.pessoas) detalhes.push(t('painel.resposta.pessoas', { n: p.pessoas }));
    if (p.latitude === null) detalhes.push(t('painel.pedidos.semLocal'));
    if (p.criadoNoCliente && Math.abs(new Date(p.criadoEm) - new Date(p.criadoNoCliente)) > 5 * 60 * 1000) {
      detalhes.push(t('painel.pedidos.offline', { quando: AB.quando(p.criadoNoCliente) }));
    }
    let acoes = '';
    if (p.telefone) acoes += '<a class="botao botao-secundario" href="tel:' + AB.escapar(p.telefone.replace(/[^0-9+]/g, '')) + '">' +
      AB.escapar(t('painel.pedidos.ligar')) + '</a>';
    if (p.latitude !== null) acoes += '<button class="botao botao-secundario" type="button" data-ver="' + p.id + '">' + AB.icone('local') +
      AB.escapar(t('mapa.verNoMapa')) + '</button>';
    if (ajuda && !p.atendido) acoes += '<button class="botao botao-primario" type="button" data-atender="' + p.id + '">' +
      AB.escapar(t('painel.pedidos.marcar')) + '</button>';
    return '<li class="' + (ajuda && !p.atendido ? 'pedido-aberto' : (p.atendido ? 'pedido-atendido' : '')) + '">' +
      '<span class="etiqueta ' + (ajuda ? 'nivel-EMERGENCIA' : 'nivel-NORMAL') + '">' + AB.icone(ajuda ? 'alerta' : 'seguro') +
      AB.escapar(p.atendido ? t('painel.pedidos.atendido') : t('painel.resposta.' + p.tipo)) + '</span>' +
      '<div class="texto"><p class="nome">' + AB.escapar(p.nome || '') + '</p>' +
      '<p class="detalhe">' + AB.escapar(detalhes.join(' · ')) + '</p>' +
      (p.mensagem ? '<p class="detalhe">“' + AB.escapar(p.mensagem) + '”</p>' : '') + '</div>' +
      (acoes ? '<div class="acoes-pedido">' + acoes + '</div>' : '') + '</li>';
  }

  async function aoClicarNosPedidos(e) {
    const atender = e.target.closest('[data-atender]');
    if (atender) {
      await enviarJson('/api/pedidos-ajuda/' + atender.dataset.atender + '/atendido', 'PATCH').catch(function () { return null; });
      carregarPedidos();
      return;
    }
    const ver = e.target.closest('[data-ver]');
    if (ver) {
      const p = estado.pedidos.find(function (x) { return String(x.id) === ver.dataset.ver; });
      if (p) estado.mapaPedidos.setView([p.latitude, p.longitude], 17);
      document.getElementById('mapa-pedidos').scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  /* ---------- Sensores ---------- */

  async function carregarLeituras() {
    const leituras = await json('/api/sensores/SGC-01/leituras').catch(function () { return []; });
    const pontos = leituras.slice().reverse();
    const dados = {
      labels: pontos.map(function (l) { return AB.dataCurta(l.dataHora.slice(0, 10)) + ' ' + l.dataHora.slice(11, 16); }),
      valores: pontos.map(function (l) { return l.nivelCm / 100; })
    };
    if (!window.Chart) return;
    if (estado.grafico) estado.grafico.destroy();
    const canvas = document.getElementById('grafico-sensor');
    canvas.setAttribute('aria-label', t('painel.sensor.grafico'));
    estado.grafico = new Chart(canvas, {
      type: 'line',
      data: { labels: dados.labels, datasets: [{ label: t('painel.sensor.grafico'), data: dados.valores,
        borderColor: '#0B4F73', backgroundColor: 'rgba(47,134,181,0.15)', fill: true, borderWidth: 3, pointRadius: 2, tension: 0 }] },
      options: {
        maintainAspectRatio: false, animation: false,
        scales: { x: { ticks: { maxRotation: 0, autoSkipPadding: 20 } },
          y: { ticks: { callback: function (v) { return AB.numero(v, 2) + ' m'; } } } },
        plugins: { legend: { display: false } }
      }
    });
  }

  /** Nova leitura ao vivo: acrescenta ao gráfico sem recarregar. */
  function aoReceberLeitura(l) {
    if (estado.grafico) {
      estado.grafico.data.labels.push(AB.dataCurta(l.dataHora.slice(0, 10)) + ' ' +
        AB.hora(l.dataHora));
      estado.grafico.data.datasets[0].data.push(l.nivelCm / 100);
      if (estado.grafico.data.labels.length > 48) {
        estado.grafico.data.labels.shift();
        estado.grafico.data.datasets[0].data.shift();
      }
      estado.grafico.update();
    }
  }

  function renderizarSensor() {
    const r = estado.risco;
    const s = r && r.sensorInfo;
    let html = '';
    if (s) {
      html += '<p><strong>' + AB.escapar(s.nome) + '</strong>' +
        (s.simulado ? '<span class="marca-simulacao">' + AB.escapar(t('painel.simulado')) + '</span>' : '') + '</p>';
      html += '<p>' + AB.escapar(s.ativo && s.nivelCm !== null
        ? t('painel.sensor.ultima', { nivel: AB.metros(s.nivelCm), quando: AB.quando(s.recebidoEm) })
        : t('painel.sensor.semDados')) + '</p>';
      if (s.simulado) html += '<p class="nota">' + AB.escapar(t('painel.sensor.simulado')) + '</p>';
    }
    if (r && r.ana) {
      html += '<p>' + AB.escapar(t('painel.sensor.ana', { nivel: AB.metros(r.ana.cotaCm) })) + '</p>';
    }
    document.getElementById('sensor').innerHTML = html;
  }

  /* ---------- Alerta manual ---------- */

  async function enviarManual(e) {
    e.preventDefault();
    const botao = document.getElementById('botao-manual');
    const mensagemTexto = document.getElementById('manual-mensagem').value.trim();
    const resultado = document.getElementById('resultado-manual');
    if (!mensagemTexto) {
      resultado.innerHTML = '<p class="mensagem-erro">' + AB.escapar(t('painel.erro')) + '</p>';
      return;
    }
    if (botao.dataset.confirmar !== 'true') {
      botao.dataset.confirmar = 'true';
      botao.querySelector('span').textContent = t('painel.manual.confirmar');
      return;
    }
    botao.dataset.confirmar = 'false';
    botao.querySelector('span').textContent = t('painel.manual.enviar');
    const nivel = document.getElementById('manual-nivel').value;
    const ok = await enviarJson('/api/alertas/manual', 'POST', {
      nivel: nivel, tipo: nivel === 'NORMAL' ? null : document.getElementById('manual-tipo').value, mensagem: mensagemTexto
    }).then(function () { return true; }).catch(function () { return false; });
    resultado.innerHTML = '<p class="' + (ok ? 'mensagem-ok' : 'mensagem-erro') + '">' +
      AB.escapar(ok ? t('painel.manual.enviado') : t('painel.erro')) + '</p>';
    if (ok) document.getElementById('manual-mensagem').value = '';
  }

  /* ---------- Alertas: canais, teste, limpeza da simulação e histórico ---------- */

  async function carregarCanais() {
    const canais = await json('/api/alertas/canais').catch(function () { return []; });
    document.getElementById('canais').innerHTML = canais.map(function (c) {
      return '<li class="' + (c.ligado ? 'canal-ligado' : 'canal-desligado') + '">' + AB.icone(c.ligado ? 'seguro' : 'info') +
        '<span><strong>' + AB.escapar(t('painel.canal.' + c.canal)) + '</strong> · ' +
        AB.escapar(c.ligado ? t('painel.canal.ligado') : t('painel.canal.desligado', { motivo: c.motivoDesligado })) + '</span></li>';
    }).join('');
  }

  async function enviarTeste() {
    const alvo = document.getElementById('resultado-teste');
    const resultados = await json('/api/alertas/teste', { method: 'POST' }).catch(function () { return null; });
    if (!resultados) { alvo.innerHTML = '<p class="mensagem-erro">' + AB.escapar(t('painel.erro')) + '</p>'; return; }
    const externos = resultados.filter(function (r) { return r.canal !== 'LOG'; });
    if (!externos.length) { alvo.innerHTML = '<p class="mensagem-alerta">' + AB.escapar(t('painel.teste.nenhum')) + '</p>'; return; }
    alvo.innerHTML = externos.map(function (r) {
      const canal = t('painel.canal.' + r.canal);
      return '<p class="' + (r.ok ? 'mensagem-ok' : 'mensagem-erro') + '">' + AB.escapar(r.ok
        ? t('painel.teste.ok', { canal: canal }) : t('painel.teste.falha', { canal: canal, erro: r.erro })) + '</p>';
    }).join('');
  }

  /** Limpeza em dois cliques (sem janela de confirmação do navegador). */
  async function limparSimulacao(e) {
    const botao = e.currentTarget;
    if (botao.dataset.confirmar !== 'true') {
      botao.dataset.confirmar = 'true';
      botao.querySelector('span').textContent = t('painel.limpar.confirmar');
      return;
    }
    botao.dataset.confirmar = 'false';
    botao.querySelector('span').textContent = t('painel.limpar');
    const comPedidos = document.getElementById('limpar-pedidos').checked;
    const r = await json('/api/simulacao/limpar' + (comPedidos ? '?pedidos=true' : ''), { method: 'POST' })
      .catch(function () { return null; });
    document.getElementById('resultado-limpar').innerHTML = r
      ? '<p class="mensagem-ok">' + AB.escapar(t('painel.limpar.feito', {
        leituras: r.leituras, alertas: r.alertas, pedidos: r.pedidos,
        nivel: t('nivel.' + r.situacaoAtual.nivel) + (r.situacaoAtual.tipo ? ' · ' + t('tipoRisco.' + r.situacaoAtual.tipo) : '')
      })) + '</p>'
      : '<p class="mensagem-erro">' + AB.escapar(t('painel.erro')) + '</p>';
    carregarAlertas();
    carregarLeituras();
    if (comPedidos) {
      document.getElementById('limpar-pedidos').checked = false;
      carregarPedidos();
    }
  }

  async function carregarAlertas() {
    const lista = await json('/api/alertas/envios').catch(function () { return []; });
    const alvo = document.getElementById('ultimos-alertas');
    if (!lista.length) { alvo.innerHTML = '<p>' + AB.escapar(t('painel.ultimos.vazio')) + '</p>'; return; }
    alvo.innerHTML = '<div class="rolagem"><table class="tabela-alertas"><tbody>' + lista.map(function (item) {
      const a = item.alerta;
      const envios = item.envios.filter(function (e) { return e.canal !== 'LOG'; }).map(function (e) {
        return t('painel.canal.' + e.canal) + ': ' + t('painel.envio.' + e.status) + (e.erro ? ' (' + e.erro + ')' : '');
      });
      return '<tr><td>' + AB.escapar(AB.quando(a.criadoEm)) + '</td><td>' + AB.etiquetaNivel(a.nivel, a.tipo) +
        (a.simulado ? '<span class="marca-simulacao">' + AB.escapar(t('painel.simulado')) + '</span>' : '') + '</td>' +
        '<td class="coluna-mensagem">' + AB.escapar(a.mensagem) +
        (envios.length ? '<div class="envios">' + AB.escapar(envios.join(' · ')) + '</div>' : '') + '</td></tr>';
    }).join('') + '</tbody></table></div>';
  }

  /* ---------- Pontos seguros ---------- */

  function mensagem(classe, texto) {
    document.getElementById('mensagem').innerHTML = texto ? '<p class="' + classe + '">' + AB.escapar(texto) + '</p>' : '';
  }

  async function carregarPontos() {
    const resposta = await fetch('/api/pontos-seguros', { cache: 'no-store' });
    estado.pontos = resposta.ok ? await resposta.json() : [];
    desenhar();
  }

  function desenhar() {
    if (estado.camada) estado.camada.remove();
    estado.camada = L.layerGroup().addTo(estado.mapa);
    estado.pontos.forEach(function (p) {
      ABMapa.marcadorDoPonto(p).bindPopup(ABMapa.balaoDoPonto(p)).addTo(estado.camada);
    });

    const alvo = document.getElementById('lista');
    if (!estado.pontos.length) {
      alvo.innerHTML = '<p>' + AB.escapar(t('painel.lista.vazia')) + '</p>';
      return;
    }
    alvo.innerHTML = '<ul class="lista-pontos">' + estado.pontos.map(function (p) {
      const detalhe = [t('tipo.' + p.tipo), t('mapa.distancia', { d: AB.distancia(p.distanciaDaSedeMetros) })];
      if (p.capacidade) detalhe.push(t('mapa.capacidade', { n: AB.numero(p.capacidade) }));
      return '<li>' + ABMapa.htmlDoMarcador(ABMapa.classeDoPonto(p), ABMapa.iconeDoPonto(p)) +
        '<div class="texto"><p class="nome">' + AB.escapar(p.nome) + '</p>' +
        '<p class="detalhe">' + AB.escapar(detalhe.join(' · ')) + '</p>' +
        (p.setorDeRisco ? '<p class="detalhe balao-risco">' +
          AB.escapar(t('mapa.emSetor', { setor: p.setorDeRisco, grau: p.grauRiscoSetor })) + '</p>' : '') +
        '</div><button class="botao botao-perigo" type="button" data-remover="' + AB.escapar(p.id.replace('dc:', '')) + '">' +
        AB.icone('lixeira') + '<span>' + AB.escapar(t('painel.remover')) + '</span></button></li>';
    }).join('') + '</ul>';
  }

  /** Remoção em dois cliques (sem janela de confirmação do navegador). */
  async function aoClicarNaLista(e) {
    const botao = e.target.closest('[data-remover]');
    if (!botao) return;
    if (botao.dataset.confirmar !== 'true') {
      botao.dataset.confirmar = 'true';
      botao.querySelector('span').textContent = t('painel.confirmarRemocao');
      return;
    }
    const resposta = await fetch('/api/pontos-seguros/' + encodeURIComponent(botao.dataset.remover), { method: 'DELETE' });
    if (resposta.status === 401) { mostrarLogin(); return; }
    if (resposta.ok || resposta.status === 404) {
      mensagem('mensagem-ok', t('painel.removido'));
      await carregarPontos();
    }
  }

  function marcarLocal(lat, lon) {
    campo('latitude').value = lat.toFixed(6);
    campo('longitude').value = lon.toFixed(6);
    if (estado.escolhido) estado.escolhido.remove();
    estado.escolhido = ABMapa.marcadorSimples(lat, lon, 'marcador-dc', 'seguro', t('painel.campo.nome')).addTo(estado.mapa);
  }

  function lerNumero(valor) {
    const texto = String(valor || '').trim().replace(',', '.');
    return texto === '' ? null : Number(texto);
  }

  async function salvar(e) {
    e.preventDefault();
    const dados = {
      nome: campo('nome').value.trim(),
      tipo: campo('tipo').value,
      latitude: lerNumero(campo('latitude').value),
      longitude: lerNumero(campo('longitude').value),
      capacidade: lerNumero(campo('capacidade').value),
      observacao: campo('observacao').value.trim() || null
    };
    const invalidos = [];
    if (!dados.nome) invalidos.push('nome');
    if (dados.latitude === null || isNaN(dados.latitude) || Math.abs(dados.latitude) > 90) invalidos.push('latitude');
    if (dados.longitude === null || isNaN(dados.longitude) || Math.abs(dados.longitude) > 180) invalidos.push('longitude');
    ['nome', 'latitude', 'longitude'].forEach(function (n) {
      campo(n).setAttribute('aria-invalid', invalidos.includes(n) ? 'true' : 'false');
    });
    if (invalidos.length) {
      mensagem('mensagem-erro', t('painel.erro'));
      campo(invalidos[0]).focus();
      return;
    }
    const ponto = await enviarJson('/api/pontos-seguros', 'POST', dados).catch(function () { return null; });
    if (!ponto) {
      mensagem('mensagem-erro', t('painel.erro'));
      return;
    }
    if (ponto.setorDeRisco) {
      mensagem('mensagem-alerta', t('painel.salvoEmRisco', { nome: ponto.nome, setor: ponto.setorDeRisco, grau: ponto.grauRiscoSetor }));
    } else {
      mensagem('mensagem-ok', t('painel.salvo', { nome: ponto.nome }));
    }
    document.getElementById('formulario').reset();
    if (estado.escolhido) { estado.escolhido.remove(); estado.escolhido = null; }
    await carregarPontos();
  }

  /* ---------- Eventos ao vivo (Server-Sent Events) ---------- */

  function indicador(aoVivo) {
    const el = document.getElementById('ao-vivo');
    el.classList.toggle('reconectando', !aoVivo);
    el.textContent = aoVivo ? t('painel.aoVivo') : t('painel.reconectando');
  }

  function conectarEventos() {
    if (estado.eventos) estado.eventos.close();
    const fonte = new EventSource('/api/painel/eventos');
    estado.eventos = fonte;
    fonte.addEventListener('conectado', function () { indicador(true); });
    fonte.addEventListener('risco', function (e) { renderizarSituacao(JSON.parse(e.data)); });
    fonte.addEventListener('leitura', function (e) { aoReceberLeitura(JSON.parse(e.data)); });
    fonte.addEventListener('alerta', function () { carregarAlertas(); });
    fonte.addEventListener('pedido', function () { carregarPedidos(); });
    fonte.onerror = async function () {
      indicador(false);
      // se a sessão acabou, volta para a senha (o navegador reconecta sozinho nos outros casos)
      const sessao = await fetch('/api/painel/sessao', { cache: 'no-store' }).then(function (r) { return r.json(); })
        .catch(function () { return null; });
      if (sessao && !sessao.autenticado) mostrarLogin();
    };
  }

  /* ---------- Início ---------- */

  async function abrirPainel() {
    document.getElementById('area-login').hidden = true;
    document.getElementById('area-painel').hidden = false;
    if (!estado.iniciado) {
      estado.iniciado = true;
      estado.mapaPedidos = ABMapa.criarMapa('mapa-pedidos', estado.comunidade);
      estado.mapa = ABMapa.criarMapa('mapa', estado.comunidade);
      ABMapa.carregarSetores().then(function (setores) {
        ABMapa.camadaDeSetores(setores).addTo(estado.mapa).bringToBack();
        ABMapa.camadaDeSetores(setores).addTo(estado.mapaPedidos).bringToBack();
      }).catch(function () { /* sem a camada de risco, o painel continua funcionando */ });
      estado.mapa.on('click', function (e) { marcarLocal(e.latlng.lat, e.latlng.lng); });
    }
    // os mapas foram criados escondidos: recalcula o tamanho
    setTimeout(function () { estado.mapa.invalidateSize(); estado.mapaPedidos.invalidateSize(); }, 50);
    indicador(false);
    const risco = await json('/api/risco').catch(function () { return null; });
    renderizarSituacao(risco);
    await Promise.all([carregarPedidos(), carregarLeituras(), carregarCanais(), carregarAlertas(), carregarPontos()]);
    conectarEventos();
  }

  async function iniciar() {
    estado.comunidade = await AB.iniciarPagina();
    campo('tipo').innerHTML = TIPOS.map(function (tipo) {
      return '<option value="' + tipo + '">' + AB.escapar(t('tipo.' + tipo)) + '</option>';
    }).join('');
    document.getElementById('manual-nivel').innerHTML = NIVEIS.map(function (n) {
      return '<option value="' + n + '">' + AB.escapar(t('nivel.' + n)) + '</option>';
    }).join('');
    document.getElementById('manual-tipo').innerHTML = ['CHEIA', 'SECA'].map(function (tipo) {
      return '<option value="' + tipo + '">' + AB.escapar(t('tipoRisco.' + tipo)) + '</option>';
    }).join('');
    AB.configurarOuvir(function () {
      const abertos = estado.pedidos.filter(function (p) { return p.tipo === 'PRECISO_DE_AJUDA' && !p.atendido; }).length;
      return t('painel.titulo') + '. ' + (estado.risco ? t('nivel.' + estado.risco.nivel) + '. ' : '') +
        t('painel.pedidos.abertos', { n: abertos }) + '.';
    });

    document.getElementById('form-login').addEventListener('submit', entrar);
    document.getElementById('botao-sair').addEventListener('click', sair);
    document.getElementById('pedidos').addEventListener('click', aoClicarNosPedidos);
    document.getElementById('form-manual').addEventListener('submit', enviarManual);
    document.getElementById('formulario').addEventListener('submit', salvar);
    document.getElementById('lista').addEventListener('click', aoClicarNaLista);
    document.getElementById('botao-teste').addEventListener('click', enviarTeste);
    document.getElementById('botao-limpar').addEventListener('click', limparSimulacao);

    const sessao = await fetch('/api/painel/sessao', { cache: 'no-store' }).then(function (r) { return r.json(); })
      .catch(function () { return { autenticado: false, senhaConfigurada: true }; });
    if (!sessao.senhaConfigurada) {
      mostrarLogin('PAINEL_TRANCADO');
    } else if (sessao.autenticado) {
      abrirPainel();
    } else {
      mostrarLogin();
    }
  }

  iniciar();
})();
