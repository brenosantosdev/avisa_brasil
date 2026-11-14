/* Tela "Preciso de ajuda": envia na hora ou guarda no celular (fila offline) e envia quando a internet voltar. */
(function () {
  'use strict';

  const AB = window.AB;
  const t = AB.t;
  const CHAVE_FILA = 'avisabrasil.filaAjuda';
  const TENTAR_DE_NOVO_MS = 20 * 1000;

  let comunidade = { nome: '' };
  let ultimaLocalizacao = null;
  let enviando = false;

  /* ---------- Fila guardada no celular (localStorage) ---------- */

  function lerFila() {
    try { return JSON.parse(localStorage.getItem(CHAVE_FILA) || '[]'); } catch (e) { return []; }
  }

  function salvarFila(fila) {
    try { localStorage.setItem(CHAVE_FILA, JSON.stringify(fila)); } catch (e) { /* sem armazenamento: só envio direto */ }
  }

  function novoId() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
  }

  /* ---------- Mensagens na tela ---------- */

  function mostrar(classe, icone, texto) {
    document.getElementById('situacao-envio').innerHTML =
      '<p class="' + classe + '">' + AB.icone(icone) + '<span>' + AB.escapar(texto) + '</span></p>' + textoDaFila();
  }

  function textoDaFila() {
    const n = lerFila().length;
    if (!n) return '';
    return '<p class="nota">' + AB.escapar(n === 1 ? t('ajuda.fila.um') : t('ajuda.fila', { n: n })) + '</p>';
  }

  /* ---------- Envio ---------- */

  /** Tenta mandar tudo que está na fila, do mais antigo para o mais novo. */
  async function enviarFila() {
    if (enviando) return;
    enviando = true;
    try {
      let fila = lerFila();
      while (fila.length) {
        const pedido = fila[0];
        let resposta;
        try {
          resposta = await fetch('/api/pedidos-ajuda', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(pedido)
          });
        } catch (e) {
          mostrar('envio-guardado', 'info', t('ajuda.guardado'));
          return; // sem internet: tenta de novo depois
        }
        if (resposta.status === 429) {
          mostrar('envio-erro', 'alerta', t('ajuda.limite'));
          return;
        }
        fila = lerFila().filter(function (p) { return p.idCliente !== pedido.idCliente; });
        salvarFila(fila);
        if (resposta.ok) {
          const dados = await resposta.json();
          const quando = AB.quando(dados.recebidoEm);
          mostrar('envio-ok', 'seguro', pedido.tipo === 'ESTOU_BEM'
            ? t('ajuda.estouBem.enviado', { quando: quando }) : t('ajuda.enviado', { quando: quando }));
        } else {
          mostrar('envio-erro', 'alerta', t('ajuda.erro'));
        }
      }
    } finally {
      enviando = false;
    }
  }

  function localizacao() {
    return new Promise(function (resolver) {
      if (!navigator.geolocation) { resolver(null); return; }
      navigator.geolocation.getCurrentPosition(function (p) {
        resolver({ latitude: Number(p.coords.latitude.toFixed(6)), longitude: Number(p.coords.longitude.toFixed(6)) });
      }, function () { resolver(null); }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 120000 });
    });
  }

  async function registrar(tipo) {
    const pedido = { tipo: tipo, idCliente: novoId(), criadoNoCliente: new Date().toISOString() };
    if (tipo === 'PRECISO_DE_AJUDA') {
      pedido.pessoas = Number(document.getElementById('pessoas').textContent);
      ['nome', 'telefone', 'mensagem'].forEach(function (campo) {
        const valor = document.getElementById(campo).value.trim();
        if (valor) pedido[campo] = valor;
      });
    }
    if (tipo === 'ESTOU_BEM' || document.getElementById('localizacao').checked) {
      mostrar('envio-guardado', 'info', t('ajuda.enviando'));
      const local = await localizacao();
      if (local) {
        pedido.latitude = local.latitude;
        pedido.longitude = local.longitude;
        ultimaLocalizacao = local;
        atualizarWhatsApp();
      } else if (tipo === 'PRECISO_DE_AJUDA') {
        mostrar('envio-guardado', 'info', t('ajuda.semLocalizacao'));
      }
    }
    // primeiro guarda no celular, depois tenta enviar: assim nada se perde se a internet cair
    salvarFila(lerFila().concat([pedido]));
    await enviarFila();
  }

  /* ---------- WhatsApp e contador de pessoas ---------- */

  function atualizarWhatsApp() {
    let texto = t('ajuda.whatsapp.texto', { lugar: comunidade.nome, n: document.getElementById('pessoas').textContent });
    if (ultimaLocalizacao) {
      texto += ' ' + t('ajuda.whatsapp.local', {
        link: 'https://www.openstreetmap.org/?mlat=' + ultimaLocalizacao.latitude + '&mlon=' + ultimaLocalizacao.longitude
      });
    }
    document.getElementById('whatsapp').href = 'https://wa.me/?text=' + encodeURIComponent(texto);
  }

  function mudarPessoas(delta) {
    const saida = document.getElementById('pessoas');
    saida.textContent = String(Math.min(200, Math.max(1, Number(saida.textContent) + delta)));
    atualizarWhatsApp();
  }

  /* ---------- Aviso de privacidade (LGPD) ---------- */

  let diasDeRetencao = 90; // o servidor diz o prazo configurado; 90 é o padrão

  function textosPrivacidade() {
    return AB.lista('ajuda.privacidade').map(function (frase) {
      return frase.split('{dias}').join(AB.numero(diasDeRetencao));
    });
  }

  async function mostrarPrivacidade() {
    const desenhar = function () {
      document.getElementById('privacidade').innerHTML = textosPrivacidade().map(function (frase) {
        const partes = frase.split(': ');
        // "Para que: ..." → rótulo em negrito e o resto normal
        return '<li>' + (partes.length > 1
          ? '<strong>' + AB.escapar(partes[0]) + ':</strong> ' + AB.escapar(partes.slice(1).join(': '))
          : AB.escapar(frase)) + '</li>';
      }).join('');
    };
    desenhar();
    try {
      const r = await (await fetch('/api/pedidos-ajuda/privacidade')).json();
      if (r && r.diasDeRetencao) {
        diasDeRetencao = r.diasDeRetencao;
        desenhar();
      }
    } catch (e) { /* sem internet: fica o prazo padrão */ }
  }

  async function iniciar() {
    comunidade = await AB.iniciarPagina();
    document.querySelector('#botao-bem .escolha-icone').innerHTML = AB.icone('seguro');
    document.querySelector('#botao-ajuda .escolha-icone').innerHTML = AB.icone('alerta');
    document.querySelectorAll('.botao-ligar').forEach(function (b) { b.insertAdjacentHTML('afterbegin', AB.icone('alerta')); });
    AB.configurarOuvir(function () {
      return [t('ajuda.ouvir'), t('ajuda.privacidade.titulo')].concat(textosPrivacidade()).join(' ');
    });
    mostrarPrivacidade();
    atualizarWhatsApp();

    document.getElementById('botao-bem').addEventListener('click', function () { registrar('ESTOU_BEM'); });
    document.getElementById('botao-ajuda').addEventListener('click', function (e) {
      const form = document.getElementById('formulario');
      form.hidden = false;
      e.currentTarget.setAttribute('aria-expanded', 'true');
      form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    document.getElementById('menos').addEventListener('click', function () { mudarPessoas(-1); });
    document.getElementById('mais').addEventListener('click', function () { mudarPessoas(1); });
    document.getElementById('formulario').addEventListener('submit', function (e) {
      e.preventDefault();
      registrar('PRECISO_DE_AJUDA');
    });

    // envia o que ficou guardado: ao abrir, quando a internet volta e de tempos em tempos
    if (lerFila().length) mostrar('envio-guardado', 'info', t('ajuda.guardado'));
    window.addEventListener('online', enviarFila);
    setInterval(function () { if (lerFila().length) enviarFila(); }, TENTAR_DE_NOVO_MS);
    enviarFila();
  }

  iniciar();
})();
