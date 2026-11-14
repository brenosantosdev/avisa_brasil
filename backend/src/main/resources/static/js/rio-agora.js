/* Tela "O rio agora": nível medido pela ANA, régua ilustrada, comparação com o GloFAS e histórico. */
(function () {
  'use strict';

  const AB = window.AB;
  const t = AB.t;

  /** Onde fica a estação, no idioma escolhido (o servidor manda em português). */
  function localDaEstacao(e) {
    const chave = 'estacao.' + e.codigo + '.localizacao';
    const texto = t(chave);
    return texto === chave ? e.localizacao : texto;
  }
  const ATUALIZAR_A_CADA_MS = 5 * 60 * 1000;
  const COR_DO_NIVEL = { NORMAL: '#1E7A46', ATENCAO: '#F2C230', ALERTA: '#C2541A', EMERGENCIA: '#A3221B' };

  const estado = { resposta: null, grafico: null };

  /** cm na régua → "7,74 m" */
  function metros(cm) {
    return t('ra.metros', { n: AB.numero(cm / 100, 2) });
  }

  /** Diferença de nível: "40 cm" até 1 m; acima disso, "6,11 m". */
  function diferenca(cm) {
    return cm < 100 ? t('ra.cm', { n: AB.numero(cm) }) : metros(cm);
  }

  /** "2026-11-11T20:45:00" (hora local da comunidade, sem fuso) → texto natural */
  function quandoLocal(dataHora) {
    return AB.quando(dataHora + '-04:00');
  }

  function dataCompleta(dataIso) {
    const d = new Date(dataIso + 'T12:00:00Z');
    return new Intl.DateTimeFormat(document.documentElement.lang, { day: '2-digit', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(d);
  }

  function ordem(nivel) {
    return ['NORMAL', 'ATENCAO', 'ALERTA', 'EMERGENCIA'].indexOf(nivel);
  }

  function textoTendencia(v) {
    if (v === null || v === undefined) return null;
    if (Math.abs(v) < 1) return t('ra.tendencia.estavel');
    return t(v > 0 ? 'ra.tendencia.sobe' : 'ra.tendencia.desce', { n: AB.numero(Math.abs(v), 1) });
  }

  /* ---------- Cartão "agora" ---------- */

  function renderizarAgora(d) {
    const alvo = document.getElementById('agora');
    if (!d || !d.atual) {
      alvo.innerHTML = AB.avisoSituacao(estado.resposta) || '';
      return;
    }
    const a = d.atual;
    let html = '<p class="rotulo">' + AB.escapar(t('ra.nivelAtual')) + '</p>';
    html += '<p class="nivel-grande">' + AB.escapar(metros(a.cotaCm)) + '</p>';
    html += '<p>' + AB.escapar(t('ra.medidoEm', { quando: quandoLocal(a.dataHora) })) + '</p>';
    const av = d.avaliacao;
    if (av) {
      const provisorias = d.cotas.cheia.origem === 'PROVISORIA' || d.cotas.seca.origem === 'PROVISORIA';
      html += '<p>' + AB.etiquetaNivel(av.nivel, av.tipo, 'etiqueta-grande') +
        (provisorias ? ' <span class="etiqueta provisoria">' + AB.icone('info') + AB.escapar(t('ra.cotas.provisoriasCurto')) + '</span>' : '') + '</p>';
      if (av.fatores.length) {
        const principal = av.fatores.slice().sort(function (x, y) { return ordem(y.nivel) - ordem(x.nivel); })[0];
        html += '<p><strong>' + AB.escapar(AB.textoFator(principal)) + '</strong></p>';
      }
    }
    const tendencia = textoTendencia(d.velocidadeCmPorDia);
    if (tendencia) {
      html += '<p class="linha-icone">' + AB.icone(d.velocidadeCmPorDia > 1 ? 'sobe' : d.velocidadeCmPorDia < -1 ? 'desce' : 'estavel') +
        '<span><strong>' + AB.escapar(tendencia) + '</strong><br><span class="nota">' + AB.escapar(t('ra.tendencia.explica')) +
        (d.variacao24hCm !== null ? ' ' + AB.escapar(t('ra.variacao24h', { sinal: (d.variacao24hCm > 0 ? '+' : '') + AB.numero(d.variacao24hCm) })) : '') +
        '</span></span></p>';
    }
    if (a.vazaoM3s) {
      html += '<p class="linha-icone">' + AB.icone('rio') + '<span>' + AB.escapar(t('ra.vazao', {
        vazao: AB.numero(a.vazaoM3s), baldes: AB.numeroCompacto(a.vazaoM3s * 50)
      })) + '</span></p>';
    }
    if (av) {
      const p = av.proximo;
      if (p) {
        html += '<p class="falta">' + AB.escapar(t('ra.falta', {
          cm: diferenca(p.faltaCm),
          nivel: t('nivel.' + p.nivel),
          tipo: t('tipoRisco.de.' + p.tipo),
          comparacao: AB.comparacaoAltura(p.faltaCm)
        })) + '</p>';
        if (p.horas) html += '<p>' + AB.escapar(t('ra.chegaEm', { tempo: AB.duracao(p.horas) })) + '</p>';
      } else if (av.nivel === 'EMERGENCIA') {
        html += '<p class="falta">' + AB.escapar(t('ra.acimaDeTodas')) + '</p>';
      }
      html += '<p><a href="/">' + AB.escapar(t('inicio.perguntaFazer')) + '</a></p>';
    } else {
      const p = d.historicoPrincipal.progresso;
      html += '<p class="nota">' + AB.escapar(t('ra.semCotas', { feito: p.anosBaixados, total: p.anosTotal })) + '</p>';
    }
    alvo.innerHTML = html;
  }

  /* ---------- Régua ilustrada (SVG em escala) ---------- */

  let dadosDaRegua = null;

  function renderizarRegua(d) {
    const alvo = document.getElementById('regua');
    dadosDaRegua = d;
    if (!d || !d.atual) { alvo.innerHTML = ''; return; }
    // No celular, a régua é desenhada na largura real da tela (1 unidade = 1 pixel) e os nomes das cotas
    // vão para uma lista abaixo dela, em letra de tamanho normal. No computador, os nomes ficam no desenho.
    const largura = alvo.clientWidth;
    const estreito = largura > 0 && largura < 520;
    const hoje = d.atual.cotaCm;
    const cheia = d.historicoPrincipal.maioresCheias[0];
    const seca = d.historicoPrincipal.maioresSecas[0];
    const c = d.cotas;
    const valores = [hoje].concat(cheia ? [cheia.cotaCm] : [], seca ? [seca.cotaCm] : [],
      c ? [c.cheia.emergenciaCm + 250, c.seca.emergenciaCm] : []);
    const minimo = Math.floor((Math.min.apply(null, valores) - 120) / 100) * 100;
    const maximo = Math.ceil((Math.max.apply(null, valores) + 120) / 100) * 100;

    const L = estreito ? Math.round(largura) : 400, A = estreito ? 520 : 540, topo = 14, base = A - 14;
    const xRegua = 54, larguraRegua = estreito ? 22 : 26;
    const y = function (cm) { return topo + (maximo - cm) / (maximo - minimo) * (base - topo); };
    const pxPorCm = (base - topo) / (maximo - minimo);
    let s = '<svg class="regua-svg' + (estreito ? ' regua-estreita' : '') + '" viewBox="0 0 ' + L + ' ' + A + '" role="img" aria-label="' +
      AB.escapar(t('ra.regua.descricao', { hoje: metros(hoje) })) + '">';

    // céu e água (a água vai do nível de hoje até o fundo)
    s += '<rect x="0" y="0" width="' + L + '" height="' + A + '" fill="#EAF3F8"/>';
    s += '<rect x="0" y="' + y(hoje) + '" width="' + L + '" height="' + (A - y(hoje)) + '" fill="#2F86B5" opacity="0.55"/>';
    s += '<path d="M0 ' + y(hoje) + ' q 12 -5 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0 t 24 0" stroke="#0B4F73" stroke-width="2" fill="none"/>';

    // faixas do semáforo na régua
    if (c) {
      // de baixo para cima: seca (vermelho, laranja, amarelo), normal (verde), cheia (amarelo, laranja, vermelho)
      const faixas = [[minimo, c.seca.emergenciaCm, 'EMERGENCIA'], [c.seca.emergenciaCm, c.seca.alertaCm, 'ALERTA'],
        [c.seca.alertaCm, c.seca.atencaoCm, 'ATENCAO'], [c.seca.atencaoCm, c.cheia.atencaoCm, 'NORMAL'],
        [c.cheia.atencaoCm, c.cheia.alertaCm, 'ATENCAO'], [c.cheia.alertaCm, c.cheia.emergenciaCm, 'ALERTA'],
        [c.cheia.emergenciaCm, maximo, 'EMERGENCIA']];
      faixas.forEach(function (f) {
        s += '<rect x="' + xRegua + '" y="' + y(f[1]) + '" width="' + larguraRegua + '" height="' + (y(f[0]) - y(f[1])) + '" fill="' + COR_DO_NIVEL[f[2]] + '"/>';
      });
    } else {
      s += '<rect x="' + xRegua + '" y="' + topo + '" width="' + larguraRegua + '" height="' + (base - topo) + '" fill="#fff" stroke="#10262E"/>';
    }
    // marcas a cada metro
    for (let cm = minimo; cm <= maximo; cm += 100) {
      s += '<line x1="' + (xRegua - 6) + '" x2="' + (xRegua + larguraRegua) + '" y1="' + y(cm) + '" y2="' + y(cm) + '" stroke="#10262E"/>';
      s += '<text x="' + (xRegua - 9) + '" y="' + (y(cm) + 4) + '" text-anchor="end">' + AB.numero(cm / 100) + ' m</text>';
    }

    // casa de palafita com o piso na cota de Emergência de cheia (ilustração) e o bonequinho de 1,60 m
    if (c) {
      const larguraCasa = estreito ? 76 : 100, xCasa = estreito ? L - larguraCasa - 14 : 280;
      const piso = y(c.cheia.emergenciaCm), parede = 250 * pxPorCm;
      for (let i = 0; i < 4; i++) {
        const x = xCasa + 8 + i * (larguraCasa - 16) / 3;
        s += '<line x1="' + x + '" x2="' + x + '" y1="' + piso + '" y2="' + base + '" stroke="#6B4A2B" stroke-width="5"/>';
      }
      s += '<rect x="' + (xCasa - 6) + '" y="' + (piso - 3) + '" width="' + (larguraCasa + 12) + '" height="6" fill="#6B4A2B"/>';
      s += '<rect x="' + xCasa + '" y="' + (piso - parede) + '" width="' + larguraCasa + '" height="' + parede + '" fill="#E3C9A1" stroke="#6B4A2B" stroke-width="2"/>';
      s += '<polygon points="' + (xCasa - 12) + ',' + (piso - parede) + ' ' + (xCasa + larguraCasa / 2) + ',' + (piso - parede - 40 * pxPorCm - 18) + ' ' + (xCasa + larguraCasa + 12) + ',' + (piso - parede) + '" fill="#A3221B"/>';
      s += '<rect x="' + (xCasa + 16) + '" y="' + (piso - 200 * pxPorCm) + '" width="' + (80 * pxPorCm + 8) + '" height="' + (200 * pxPorCm) + '" fill="#6B4A2B"/>';
      // bonequinho de 1,60 m em pé no piso
      const altura = 160 * pxPorCm, xp = xCasa + larguraCasa - 26, cabeca = altura * 0.13;
      s += '<g stroke="#10262E" stroke-width="3" stroke-linecap="round" fill="none">' +
        '<circle cx="' + xp + '" cy="' + (piso - altura + cabeca) + '" r="' + cabeca + '" fill="#F3F6F5"/>' +
        '<line x1="' + xp + '" y1="' + (piso - altura + 2 * cabeca) + '" x2="' + xp + '" y2="' + (piso - altura * 0.45) + '"/>' +
        '<line x1="' + xp + '" y1="' + (piso - altura * 0.45) + '" x2="' + (xp - altura * 0.12) + '" y2="' + piso + '"/>' +
        '<line x1="' + xp + '" y1="' + (piso - altura * 0.45) + '" x2="' + (xp + altura * 0.12) + '" y2="' + piso + '"/>' +
        '<line x1="' + (xp - altura * 0.18) + '" y1="' + (piso - altura * 0.68) + '" x2="' + (xp + altura * 0.18) + '" y2="' + (piso - altura * 0.68) + '"/>' +
        '</g>';
    }

    // linhas de referência: cotas, maior cheia, maior seca e hoje
    const linhas = [];
    if (cheia) linhas.push([cheia.cotaCm, '#4A6169', t('ra.regua.maiorCheia', { ano: cheia.ano }), true]);
    if (c) {
      linhas.push([c.cheia.emergenciaCm, '#A3221B', t('ra.regua.cheia', { nivel: t('nivel.EMERGENCIA') }), true]);
      linhas.push([c.cheia.alertaCm, '#C2541A', t('ra.regua.cheia', { nivel: t('nivel.ALERTA') }), true]);
      linhas.push([c.cheia.atencaoCm, '#8A6A00', t('ra.regua.cheia', { nivel: t('nivel.ATENCAO') }), true]);
      linhas.push([c.seca.atencaoCm, '#8A6A00', t('ra.regua.seca', { nivel: t('nivel.ATENCAO') }), true]);
      linhas.push([c.seca.alertaCm, '#C2541A', t('ra.regua.seca', { nivel: t('nivel.ALERTA') }), true]);
      linhas.push([c.seca.emergenciaCm, '#A3221B', t('ra.regua.seca', { nivel: t('nivel.EMERGENCIA') }), true]);
    }
    if (seca) linhas.push([seca.cotaCm, '#8A5A00', t('ra.regua.maiorSeca', { ano: seca.ano }), true]);
    linhas.push([hoje, '#0B4F73', t('ra.regua.hoje'), false, true]);
    linhas.sort(function (a, b) { return b[0] - a[0]; });
    // as linhas ficam na altura real; só os rótulos são afastados quando as cotas estão muito próximas
    let ultimoRotulo = -Infinity;
    linhas.forEach(function (l) {
      const yl = y(l[0]);
      s += '<line x1="' + (xRegua + larguraRegua) + '" x2="' + (L - 6) + '" y1="' + yl + '" y2="' + yl + '" stroke="' + l[1] + '" stroke-width="' + (l[4] ? 3 : 2) + '"' +
        (l[3] ? ' stroke-dasharray="6 4"' : '') + '/>';
      if (estreito && !l[4]) return; // no celular, só "Hoje" fica escrito no desenho
      const yr = Math.max(yl - 4, ultimoRotulo + 15);
      ultimoRotulo = yr;
      s += '<text x="' + (xRegua + larguraRegua + 6) + '" y="' + yr + '" class="' + (l[4] ? 'rotulo-cota' : '') + '" fill="' + l[1] + '"' +
        ' paint-order="stroke" stroke="#EAF3F8" stroke-width="3">' + AB.escapar(l[2] + ' · ' + metros(l[0])) + '</text>';
    });
    s += '</svg>';

    // lista das linhas, de cima para baixo, com o mesmo traço e a mesma cor do desenho
    const lista = estreito ? '<ul class="regua-lista">' + linhas.map(function (l) {
      return '<li><svg viewBox="0 0 32 10" aria-hidden="true"><line x1="0" x2="32" y1="5" y2="5" stroke="' + l[1] + '" stroke-width="' +
        (l[4] ? 4 : 3) + '"' + (l[3] ? ' stroke-dasharray="6 4"' : '') + '/></svg><span' + (l[4] ? ' class="rotulo-hoje"' : '') + '>' +
        AB.escapar(l[2]) + '</span><strong>' + AB.escapar(metros(l[0])) + '</strong></li>';
    }).join('') + '</ul>' : '';

    alvo.innerHTML = s + lista + '<p class="nota">' + AB.escapar(t('ra.regua.legenda', { estacao: d.estacao.nome })) + '</p>';
  }

  // girar o celular ou mudar o tamanho da janela redesenha a régua na nova largura
  let esperaRedesenho;
  window.addEventListener('resize', function () {
    clearTimeout(esperaRedesenho);
    esperaRedesenho = setTimeout(function () { if (dadosDaRegua) renderizarRegua(dadosDaRegua); }, 200);
  });

  /* ---------- Medição × modelo ---------- */

  function renderizarComparacao(d) {
    const alvo = document.getElementById('comparacao');
    if (!d) { alvo.innerHTML = ''; return; }
    let html = '<h2>' + AB.escapar(t('ra.comparacao.titulo')) + '</h2>';
    if (!d.comparacaoAna) {
      html += '<p>' + AB.escapar(t('ra.comparacao.semNormal')) + '</p>';
    } else {
      html += '<ul class="comparacao-lista">';
      html += '<li>' + AB.icone('regua') + '<span><strong>' + AB.escapar(t('ra.comparacao.ana', { texto: t('rio.normal.' + d.comparacaoAna) })) + '</strong></span></li>';
      if (d.comparacaoGlofas) {
        html += '<li>' + AB.icone('info') + '<span>' + AB.escapar(t('ra.comparacao.glofas', { texto: t('rio.normal.' + d.comparacaoGlofas) })) + '</span></li>';
      }
      html += '</ul>';
      if (d.divergem) html += '<p class="vale-ana">' + AB.escapar(t('ra.comparacao.divergem')) + '</p>';
      else if (d.comparacaoGlofas) html += '<p class="concordam">' + AB.escapar(t('ra.comparacao.concordam')) + '</p>';
      const n = d.normalHoje;
      html += '<p class="nota">' + AB.escapar(t('ra.normal.metodo', {
        ini: n.anoInicial, fim: n.anoFinal, p25: AB.numero(n.p25 / 100, 2), p75: AB.numero(n.p75 / 100, 2),
        data: AB.dataCurta(AB.hojeNaComunidade()), amostras: AB.numero(n.amostras)
      })) + '</p>';
    }
    alvo.innerHTML = html;
  }

  /* ---------- Gráfico dos últimos 7 dias ---------- */

  function desenharGrafico(d) {
    if (!window.Chart || !d || !d.ultimosDias.length) return;
    if (estado.grafico) estado.grafico.destroy();
    const canvas = document.getElementById('grafico');
    canvas.setAttribute('aria-label', t('ra.grafico.descricao'));
    const rotulos = d.ultimosDias.map(function (p) {
      return AB.dataCurta(p.dataHora.slice(0, 10)) + ' ' + p.dataHora.slice(11, 13) + 'h';
    });
    estado.grafico = new Chart(canvas, {
      type: 'line',
      data: {
        labels: rotulos,
        datasets: [{
          label: t('ra.grafico.nivel'), data: d.ultimosDias.map(function (p) { return p.cotaCm / 100; }),
          borderColor: '#0B4F73', backgroundColor: 'rgba(47,134,181,0.15)', fill: true, borderWidth: 3, pointRadius: 0, tension: 0
        }]
      },
      options: {
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: { ticks: { maxRotation: 0, autoSkipPadding: 24, font: { size: 14 } }, grid: { display: false } },
          // a régua mede de 1 em 1 cm: marcas com no mínimo 1 cm, sem rótulos repetidos
          y: { ticks: { font: { size: 14 }, precision: 2, callback: function (v) { return AB.numero(v, 2) + ' m'; } } }
        },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: function (ctx) { return AB.numero(ctx.raw, 2) + ' m'; } } }
        }
      }
    });
  }

  /* ---------- Já aconteceu antes? ---------- */

  function tabela(classe, titulo, eventos) {
    if (!eventos.length) return '<p class="nota">' + AB.escapar(t('ra.historico.vazio')) + '</p>';
    return '<table class="tabela-eventos ' + classe + '"><caption>' + AB.escapar(titulo) + '</caption>' +
      '<thead><tr><th scope="col">' + AB.escapar(t('ra.historico.data')) + '</th><th scope="col">' + AB.escapar(t('ra.historico.cota')) + '</th></tr></thead><tbody>' +
      eventos.map(function (e) {
        return '<tr><td>' + AB.escapar(dataCompleta(e.data)) + '</td><td>' + AB.escapar(metros(e.cotaCm)) + '</td></tr>';
      }).join('') + '</tbody></table>';
  }

  function cartaoHistorico(h, hojeCm) {
    const e = h.estacao;
    let html = '<div class="cartao"><h3>' + AB.escapar(t('ra.historico.estacao', { nome: e.nome, codigo: e.codigo, localizacao: localDaEstacao(e) })) + '</h3>';
    if (h.primeiraLeitura) {
      html += '<p class="nota">' + AB.escapar(t('ra.historico.periodo', {
        ini: h.primeiraLeitura.slice(0, 4), fim: h.ultimaLeitura.slice(0, 4), anos: h.anosCompletos
      })) + ' ' + AB.escapar(t('ra.historico.atraso', { data: dataCompleta(h.ultimaLeitura) })) + '</p>';
    }
    if (h.progresso.anosBaixados < h.progresso.anosTotal) {
      html += '<p class="aviso-situacao aviso-DESATUALIZADO">' + AB.icone('info') + '<span>' +
        AB.escapar(t('ra.historico.baixando', { feito: h.progresso.anosBaixados, total: h.progresso.anosTotal })) + '</span></p>';
    }
    html += tabela('cheia', t('ra.historico.cheias'), h.maioresCheias);
    html += tabela('seca', t('ra.historico.secas'), h.maioresSecas);
    if (hojeCm !== null && h.maioresCheias.length) {
      const dif = h.maioresCheias[0].cotaCm - hojeCm;
      if (dif > 0) html += '<p>' + AB.escapar(t('ra.historico.hoje', { dif: metros(dif) })) + '</p>';
    }
    return html + '</div>';
  }

  function renderizarHistorico(d) {
    const alvo = document.getElementById('historico');
    if (!d) { alvo.innerHTML = ''; return; }
    alvo.innerHTML = cartaoHistorico(d.historicoPrincipal, d.atual ? d.atual.cotaCm : null) +
      cartaoHistorico(d.historicoCidade, null) +
      '<p class="nota">' + AB.escapar(t('ra.historico.reguasDiferentes')) + '</p>';
  }

  /* ---------- Fontes e voz ---------- */

  function renderizarFontes(r) {
    const d = r.dados;
    let extra = d ? '<p>' + AB.escapar(t('ra.fonte.estacao', { codigo: d.estacao.codigo, nome: d.estacao.nome })) + '</p>' : '';
    if (d && d.cotas) {
      ['cheia', 'seca'].forEach(function (tipo) {
        const f = d.cotas[tipo];
        extra += '<p><strong>' + AB.escapar(t('ra.cotas.' + tipo + '.' + f.origem, {
          ini: f.anoInicial, fim: f.anoFinal, anos: f.anos, fonte: f.fonte
        })) + '</strong></p>';
      });
    }
    document.getElementById('fontes').innerHTML = r.fonte ? AB.rodapeFonte(r, extra) : '';
  }

  function textoParaOuvir() {
    const r = estado.resposta;
    const d = r && r.dados;
    const partes = [t('ra.titulo') + '. ' + document.getElementById('intro').textContent];
    if (d && d.atual) {
      partes.push(t('ra.nivelAtual') + ': ' + metros(d.atual.cotaCm) + '. ' + t('ra.medidoEm', { quando: quandoLocal(d.atual.dataHora) }) + '.');
      const av = d.avaliacao;
      if (av) {
        partes.push(t('nivel.' + av.nivel) + (av.tipo ? ', ' + t('tipoRisco.' + av.tipo) : '') + ', ' + t('ra.cotas.provisoriasCurto') + '.');
        av.fatores.forEach(function (f) { partes.push(AB.textoFator(f)); });
      }
      const tendencia = textoTendencia(d.velocidadeCmPorDia);
      if (tendencia) partes.push(tendencia + '.');
      if (av && av.proximo) {
        partes.push(t('ra.falta', { cm: diferenca(av.proximo.faltaCm), nivel: t('nivel.' + av.proximo.nivel),
          tipo: t('tipoRisco.de.' + av.proximo.tipo), comparacao: AB.comparacaoAltura(av.proximo.faltaCm) }));
      }
      if (d.comparacaoAna) partes.push(t('ra.comparacao.ana', { texto: t('rio.normal.' + d.comparacaoAna) }) + '.');
      if (d.divergem) partes.push(t('ra.comparacao.divergem'));
    }
    if (r && r.situacao !== 'ATUALIZADO') partes.push(t('situacao.' + r.situacao, { quando: r.atualizadoEm ? AB.quando(r.atualizadoEm) : '' }));
    return partes.join(' ');
  }

  async function carregar() {
    let r;
    try {
      r = await (await fetch('/api/rio/agora', { cache: 'no-store' })).json();
    } catch (e) {
      r = { situacao: 'INDISPONIVEL', dados: null };
    }
    estado.resposta = r;
    const d = r.dados;
    if (d) {
      document.getElementById('intro').textContent = t('ra.intro', { rio: d.estacao.rio, estacao: d.estacao.nome, localizacao: localDaEstacao(d.estacao) });
    }
    document.getElementById('aviso-situacao').innerHTML = AB.avisoSituacao(r);
    renderizarAgora(d);
    renderizarRegua(d);
    renderizarComparacao(d);
    desenharGrafico(d);
    renderizarHistorico(d);
    renderizarFontes(r);
  }

  async function iniciar() {
    await AB.iniciarPagina();
    AB.configurarOuvir(textoParaOuvir);
    await carregar();
    setInterval(carregar, ATUALIZAR_A_CADA_MS);
  }

  iniciar();
})();
