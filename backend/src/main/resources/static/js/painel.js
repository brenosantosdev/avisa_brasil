/* Painel da Defesa Civil: cadastro de pontos seguros (aparecem no mapa da comunidade). */
(function () {
  'use strict';

  const AB = window.AB;
  const ABMapa = window.ABMapa;
  const t = AB.t;
  const TIPOS = ['ABRIGO', 'ESCOLA', 'SAUDE', 'APOIO'];

  const estado = { comunidade: null, mapa: null, camada: null, escolhido: null, pontos: [] };

  const campo = function (nome) { return document.getElementById('campo-' + nome); };

  function mensagem(classe, texto) {
    document.getElementById('mensagem').innerHTML = texto ? '<p class="' + classe + '">' + AB.escapar(texto) + '</p>' : '';
  }

  /* ---------- Lista e mapa dos pontos cadastrados ---------- */

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
    if (resposta.ok || resposta.status === 404) {
      mensagem('mensagem-ok', t('painel.removido'));
      await carregarPontos();
    }
  }

  /* ---------- Formulário ---------- */

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

    const resposta = await fetch('/api/pontos-seguros', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dados)
    }).catch(function () { return null; });
    if (!resposta || !resposta.ok) {
      mensagem('mensagem-erro', t('painel.erro'));
      return;
    }
    const ponto = await resposta.json();
    if (ponto.setorDeRisco) {
      mensagem('mensagem-alerta', t('painel.salvoEmRisco', { nome: ponto.nome, setor: ponto.setorDeRisco, grau: ponto.grauRiscoSetor }));
    } else {
      mensagem('mensagem-ok', t('painel.salvo', { nome: ponto.nome }));
    }
    document.getElementById('formulario').reset();
    if (estado.escolhido) { estado.escolhido.remove(); estado.escolhido = null; }
    await carregarPontos();
  }

  async function iniciar() {
    estado.comunidade = await AB.iniciarPagina();
    document.getElementById('aviso-acesso').innerHTML = AB.icone('alerta') + '<span>' + AB.escapar(t('painel.aviso')) + '</span>';
    campo('tipo').innerHTML = TIPOS.map(function (tipo) {
      return '<option value="' + tipo + '">' + AB.escapar(t('tipo.' + tipo)) + '</option>';
    }).join('');
    AB.configurarOuvir(function () { return t('painel.ouvir', { n: estado.pontos.length }); });

    estado.mapa = ABMapa.criarMapa('mapa', estado.comunidade);
    ABMapa.carregarSetores().then(function (setores) {
      ABMapa.camadaDeSetores(setores).addTo(estado.mapa).bringToBack();
    }).catch(function () { /* sem a camada de risco, o cadastro continua funcionando */ });
    estado.mapa.on('click', function (e) { marcarLocal(e.latlng.lat, e.latlng.lng); });

    document.getElementById('formulario').addEventListener('submit', salvar);
    document.getElementById('lista').addEventListener('click', aoClicarNaLista);
    await carregarPontos();
  }

  iniciar();
})();
