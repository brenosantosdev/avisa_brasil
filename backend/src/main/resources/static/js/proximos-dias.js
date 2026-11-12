/* Tela "Próximos dias": chuva por dia (Open-Meteo) e vazão do rio (GloFAS). */
(function () {
  'use strict';

  const AB = window.AB;
  const t = AB.t;
  const ATUALIZAR_A_CADA_MS = 10 * 60 * 1000;

  const estado = { comunidade: null, tempo: null, rio: null, grafico: null };

  const ICONE_DA_TENDENCIA = { SUBINDO: 'sobe', DESCENDO: 'desce', ESTAVEL: 'estavel' };

  async function buscar(url) {
    try {
      const resposta = await fetch(url, { cache: 'no-store' });
      if (!resposta.ok) throw new Error('HTTP ' + resposta.status);
      return await resposta.json();
    } catch (e) {
      // servidor fora do ar: tratado como dado indisponível
      return { situacao: 'INDISPONIVEL', fonte: '', fonteUrl: '#', atualizadoEm: null, dados: null };
    }
  }

  /* ---------- Chuva ---------- */

  function iconeDaChuva(dia) {
    // "sem chuva" nunca mostra nuvem com chuva, mesmo que o código do céu indique garoa
    if (dia.categoriaChuva === 'SEM_CHUVA') return dia.ceu === 'LIMPO' ? 'sol' : (dia.ceu === 'NEBLINA' ? 'neblina' : 'nuvem');
    return dia.ceu === 'TROVOADA' ? 'trovoada' : 'chuva';
  }

  /** "Amanhã à tarde" */
  function diaEPeriodo(dia) {
    const nome = AB.nomeDoDia(dia.data);
    return dia.periodoMaisChuvoso ? nome + ' ' + t('periodo.' + dia.periodoMaisChuvoso) : nome;
  }

  /** Baldes desenhados: um balde para cada 20 litros por m², até 5. */
  function desenharBaldes(baldes) {
    const quantidade = Math.min(5, Math.max(1, Math.ceil(baldes)));
    let html = '<div class="baldes" aria-hidden="true">';
    for (let i = 0; i < quantidade; i++) {
      const cheio = Math.max(0, Math.min(1, baldes - i));
      html += '<div class="balde"><div class="balde-agua" style="height:' + Math.round(cheio * 100) + '%"></div></div>';
    }
    if (baldes > 5) html += '<span class="baldes-mais">+</span>';
    return html + '</div>';
  }

  function cartaoDoDia(dia) {
    const categoria = t('chuva.' + dia.categoriaChuva);
    const hoje = dia.data === AB.hojeNaComunidade();
    let html = '<article class="cartao dia' + (hoje ? ' dia-hoje' : '') + '">';
    html += '<div class="dia-cabecalho"><h3 class="dia-nome">' + AB.escapar(AB.nomeDoDia(dia.data)) + '</h3>' +
      '<span class="dia-data">' + AB.dataCurta(dia.data) + '</span></div>';
    html += '<div><span class="etiqueta chuva-' + dia.categoriaChuva + '">' + AB.icone(iconeDaChuva(dia)) +
      AB.escapar(categoria) + '</span></div>';
    html += '<div class="dia-chuva"><span class="dia-mm">' + AB.escapar(t('pd.chuva.mm', { mm: AB.numero(dia.chuvaMm, 1) })) +
      '</span>' + desenharBaldes(dia.baldesPorM2) + '</div>';
    html += '<p class="dia-baldes-texto">' + AB.escapar(AB.textoBaldes(dia.chuvaMm)) + '</p>';
    if (dia.chanceChuva !== null) {
      html += '<p class="dia-info">' + AB.icone('gota') + AB.escapar(t('pd.chuva.chance', { n: dia.chanceChuva })) + '</p>';
    }
    if (dia.periodoMaisChuvoso) {
      html += '<p class="dia-info">' + AB.icone('chuva') +
        AB.escapar(t('pd.chuva.maisChuva', { periodo: t('periodo.' + dia.periodoMaisChuvoso) })) + '</p>';
    }
    if (dia.temperaturaMinima !== null && dia.temperaturaMaxima !== null) {
      html += '<p class="dia-info">' + AB.icone('termometro') + AB.escapar(t('pd.chuva.temperatura', {
        min: AB.numero(dia.temperaturaMinima), max: AB.numero(dia.temperaturaMaxima)
      })) + '</p>';
    }
    if (dia.calorForte) {
      html += '<div><span class="etiqueta calor">' + AB.icone('sol') + AB.escapar(t('pd.chuva.calorForte')) + '</span></div>';
    }
    return html + '</article>';
  }

  function renderizarChuva() {
    const resposta = estado.tempo;
    const alvo = document.getElementById('chuva');
    let html = AB.avisoSituacao(resposta);
    if (resposta.dados) {
      html += '<div class="dias">' + resposta.dados.dias.map(cartaoDoDia).join('') + '</div>';
      html += '<p class="explica">' + AB.icone('info') + '<span>' + AB.escapar(t('pd.chuva.explica')) + '</span></p>';
    }
    html += resposta.fonte ? AB.rodapeFonte(resposta) : '';
    alvo.innerHTML = html;
  }

  function resumoDaChuva() {
    const dados = estado.tempo && estado.tempo.dados;
    if (!dados) return null;
    const maior = dados.dias.reduce(function (a, b) { return b.chuvaMm > a.chuvaMm ? b : a; });
    const diasComChuva = dados.dias.filter(function (d) { return d.categoriaChuva !== 'SEM_CHUVA'; }).length;
    if (maior.categoriaChuva === 'SEM_CHUVA') {
      return { icone: 'sol', titulo: t('pd.resumo.chuva.nenhuma'), detalhe: '' };
    }
    return {
      icone: iconeDaChuva(maior),
      titulo: t('pd.resumo.chuva.maior', {
        quando: diaEPeriodo(maior),
        categoria: t('chuva.' + maior.categoriaChuva),
        mm: AB.numero(maior.chuvaMm, 1)
      }),
      detalhe: t('pd.resumo.chuva.dias', { n: diasComChuva }) + ' ' + AB.textoBaldes(maior.chuvaMm) + '.'
    };
  }

  /* ---------- Rio ---------- */

  function tituloDaTendencia(resumo) {
    if (!resumo.tendencia) return t('rio.tendencia.semDados');
    const rapido = resumo.mudancaRapida && resumo.tendencia !== 'ESTAVEL' ? '.rapido' : '';
    return t('rio.tendencia.' + resumo.tendencia + rapido);
  }

  function variacaoComSinal(valor) {
    return new Intl.NumberFormat(document.documentElement.lang, {
      maximumFractionDigits: 1, signDisplay: 'exceptZero'
    }).format(valor);
  }

  function renderizarRio() {
    const resposta = estado.rio;
    const alvo = document.getElementById('rio');
    let html = AB.avisoSituacao(resposta);
    const dados = resposta.dados;
    if (dados) {
      const r = dados.resumo;
      html += '<div class="rio-destaques">';

      // 1. Vai subir ou descer?
      html += '<div class="cartao destaque"><div class="destaque-icone">' +
        AB.icone(ICONE_DA_TENDENCIA[r.tendencia] || 'regua') + '</div><div>' +
        '<p class="destaque-titulo">' + AB.escapar(tituloDaTendencia(r)) + '</p>';
      if (r.tendencia) {
        html += '<p>' + AB.escapar(t('rio.tendencia.detalhe', {
          hoje: AB.numero(r.vazaoHoje), em7: AB.numero(r.vazaoEm7Dias), variacao: variacaoComSinal(r.variacaoPercentual7Dias)
        })) + '</p>';
      }
      if (r.vazaoPico && r.vazaoHoje && r.vazaoPico > r.vazaoHoje * 1.05) {
        const ultimo = dados.dias[dados.dias.length - 1].data;
        html += '<p>' + AB.escapar(t('rio.pico', {
          fim: AB.dataCurta(ultimo),
          quando: AB.nomeDoDia(r.dataPico) + ', ' + AB.dataCurta(r.dataPico),
          vazao: AB.numero(r.vazaoPico)
        })) + '</p>';
      }
      html += '</div></div>';

      // 2. Está normal para a época?
      html += '<div class="cartao destaque"><div class="destaque-icone">' + AB.icone('regua') + '</div><div>';
      if (r.comparacaoHoje && r.normalHoje) {
        html += '<p class="destaque-titulo"><span class="etiqueta normal-' + r.comparacaoHoje + '">' +
          AB.icone(r.comparacaoHoje.indexOf('MUITO') === 0 ? 'alerta' : 'info') +
          AB.escapar(t('rio.normal.' + r.comparacaoHoje)) + '</span></p>';
        html += '<p>' + AB.escapar(t('rio.normal.detalhe', {
          p25: AB.numero(r.normalHoje.p25), p75: AB.numero(r.normalHoje.p75),
          ini: r.normalHoje.anoInicial, fim: r.normalHoje.anoFinal
        })) + '</p>';
        html += '<p class="nota">' + AB.escapar(t('rio.normal.metodo', {
          ini: r.normalHoje.anoInicial, fim: r.normalHoje.anoFinal, janela: r.normalHoje.janelaDias,
          data: AB.dataCurta(dados.hoje), amostras: AB.numero(r.normalHoje.amostras)
        })) + '</p>';
        if (r.comparacaoHoje === 'MUITO_ABAIXO') html += '<p><strong>' + AB.escapar(t('rio.normal.seca')) + '</strong></p>';
        if (r.comparacaoHoje === 'MUITO_ACIMA') html += '<p><strong>' + AB.escapar(t('rio.normal.cheia')) + '</strong></p>';
      } else {
        html += '<p>' + AB.escapar(t('rio.normal.semHistorico')) + '</p>';
      }
      html += '</div></div></div>';

      // 3. Gráfico
      html += '<div class="cartao grafico-rio"><h3>' + AB.escapar(t('rio.grafico.titulo')) + '</h3>' +
        '<div class="grafico-caixa"><canvas id="grafico-rio" role="img" aria-label="' +
        AB.escapar(t('rio.grafico.descricao')) + '"></canvas></div>';

      // 4. Explicações
      html += '<div class="rio-textos">';
      if (r.baldesPorSegundoHoje) {
        let baldes = t('rio.baldes', { hoje: AB.numeroCompacto(r.baldesPorSegundoHoje) });
        if (r.normalHoje) baldes += ' ' + t('rio.baldes.normal', { normal: AB.numeroCompacto(r.normalHoje.mediana * 50) });
        html += '<p>' + AB.escapar(baldes) + '</p>';
      }
      html += '<p class="nota">' + AB.escapar(t('rio.incerteza')) + '</p>';
      html += '<p class="nota">' + AB.escapar(t('rio.unidade')) + '</p>';
      const h = dados.historico;
      if (h && h.vazaoMaximo) {
        html += '<p class="nota">' + AB.escapar(t('rio.extremos', {
          ano: h.inicio.slice(0, 4),
          max: AB.numero(h.vazaoMaximo), dataMax: AB.mesEAno(h.dataMaximo),
          min: AB.numero(h.vazaoMinimo), dataMin: AB.mesEAno(h.dataMinimo)
        })) + '</p>';
      }
      html += '</div>';
      html += AB.rodapeFonte(resposta, '<p>' + AB.escapar(t('rio.aviso', { rio: dados.rio })) + '</p>');
      html += '</div>';
    } else if (resposta.fonte) {
      html += AB.rodapeFonte(resposta);
    }
    alvo.innerHTML = html;
    if (dados) desenharGrafico(dados);
  }

  /** Linha vertical marcando "hoje" no gráfico. */
  const marcaHoje = {
    id: 'marcaHoje',
    afterDatasetsDraw: function (grafico, args, opcoes) {
      if (opcoes.indice < 0) return;
      const x = grafico.scales.x.getPixelForValue(opcoes.indice);
      const area = grafico.chartArea;
      const ctx = grafico.ctx;
      ctx.save();
      ctx.strokeStyle = '#10262E';
      ctx.lineWidth = 2;
      ctx.setLineDash([4, 4]);
      ctx.beginPath();
      ctx.moveTo(x, area.top);
      ctx.lineTo(x, area.bottom);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = '#10262E';
      ctx.font = 'bold 14px "Atkinson Hyperlegible", sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(opcoes.rotulo, x + 6, area.top + 14);
      ctx.restore();
    }
  };

  function desenharGrafico(dados) {
    if (!window.Chart) return; // sem internet para carregar o Chart.js: o texto continua valendo
    if (estado.grafico) estado.grafico.destroy();
    const dias = dados.dias;
    const indiceHoje = dias.findIndex(function (d) { return d.data === dados.hoje; });
    const mapa = function (campo) { return dias.map(function (d) { return d[campo]; }); };

    estado.grafico = new Chart(document.getElementById('grafico-rio'), {
      type: 'line',
      data: {
        labels: dias.map(function (d) { return AB.dataCurta(d.data); }),
        datasets: [
          { label: '', data: mapa('normalP25'), borderWidth: 0, pointRadius: 0, fill: false },
          {
            label: t('rio.grafico.normal'), data: mapa('normalP75'), borderWidth: 0, pointRadius: 0,
            fill: '-1', backgroundColor: 'rgba(74, 97, 105, 0.18)'
          },
          { label: '', data: mapa('p25'), borderWidth: 0, pointRadius: 0, fill: false },
          {
            label: t('rio.grafico.faixa'), data: mapa('p75'), borderWidth: 0, pointRadius: 0,
            fill: '-1', backgroundColor: 'rgba(47, 134, 181, 0.28)'
          },
          {
            label: t('rio.grafico.vazao'), data: mapa('vazao'), borderColor: '#0B4F73', borderWidth: 3,
            pointRadius: 0, pointHoverRadius: 5, tension: 0.2,
            segment: { borderDash: function (ctx) { return ctx.p0DataIndex >= indiceHoje ? [8, 6] : undefined; } }
          }
        ]
      },
      options: {
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: { ticks: { maxRotation: 0, autoSkipPadding: 16, font: { size: 14 } }, grid: { display: false } },
          y: {
            title: { display: true, text: t('rio.grafico.eixo'), font: { size: 14 } },
            ticks: { font: { size: 14 }, callback: function (v) { return AB.numero(v); } }
          }
        },
        plugins: {
          legend: {
            labels: {
              filter: function (item) { return item.text !== ''; },
              font: { size: 15 }, boxWidth: 18
            }
          },
          tooltip: {
            filter: function (item) { return item.dataset.label !== '' && item.raw !== null; },
            callbacks: {
              label: function (ctx) { return ctx.dataset.label + ': ' + AB.numero(ctx.raw) + ' m³/s'; }
            }
          },
          marcaHoje: { indice: indiceHoje, rotulo: t('rio.grafico.hoje') }
        }
      },
      plugins: [marcaHoje]
    });
  }

  /* ---------- Resumo e voz ---------- */

  function renderizarResumo() {
    const linhas = [];
    const chuva = resumoDaChuva();
    if (chuva) linhas.push(chuva);
    const rio = estado.rio && estado.rio.dados;
    if (rio) {
      const r = rio.resumo;
      linhas.push({
        icone: ICONE_DA_TENDENCIA[r.tendencia] || 'rio',
        titulo: tituloDaTendencia(r) + '.',
        detalhe: r.comparacaoHoje ? t('rio.normal.' + r.comparacaoHoje) + '.' : ''
      });
    }
    const alvo = document.getElementById('resumo');
    if (!linhas.length) {
      alvo.innerHTML = AB.avisoSituacao({ situacao: 'INDISPONIVEL' });
      return;
    }
    alvo.innerHTML = linhas.map(function (l) {
      return '<div class="resumo-linha"><div class="resumo-icone">' + AB.icone(l.icone) + '</div><div>' +
        '<p class="resumo-titulo">' + AB.escapar(l.titulo) + '</p>' +
        (l.detalhe ? '<p class="resumo-detalhe">' + AB.escapar(l.detalhe) + '</p>' : '') + '</div></div>';
    }).join('');
  }

  /** Texto lido pelo botão "Ouvir". */
  function textoParaOuvir() {
    const partes = [t('pd.titulo') + '. ' + document.getElementById('intro').textContent];
    const chuva = resumoDaChuva();
    if (chuva) partes.push(chuva.titulo + ' ' + chuva.detalhe);
    if (estado.tempo && estado.tempo.dados) {
      estado.tempo.dados.dias.forEach(function (d) {
        partes.push(AB.nomeDoDia(d.data) + ': ' + t('chuva.' + d.categoriaChuva) + '. ' +
          (d.chuvaMm >= 1 ? AB.textoBaldes(d.chuvaMm) + '.' : ''));
      });
    }
    const rio = estado.rio && estado.rio.dados;
    if (rio) {
      partes.push(tituloDaTendencia(rio.resumo) + '.');
      if (rio.resumo.comparacaoHoje) {
        partes.push(t('rio.normal.' + rio.resumo.comparacaoHoje) + '.');
        if (rio.resumo.comparacaoHoje === 'MUITO_ABAIXO') partes.push(t('rio.normal.seca'));
        if (rio.resumo.comparacaoHoje === 'MUITO_ACIMA') partes.push(t('rio.normal.cheia'));
      }
    }
    [estado.tempo, estado.rio].forEach(function (r) {
      if (r && r.situacao !== 'ATUALIZADO') {
        partes.push(t('situacao.' + r.situacao, { quando: r.atualizadoEm ? AB.quando(r.atualizadoEm) : '' }));
      }
    });
    return partes.join(' ');
  }

  async function carregarDados() {
    const resultados = await Promise.all([buscar('/api/previsao/tempo'), buscar('/api/previsao/rio')]);
    estado.tempo = resultados[0];
    estado.rio = resultados[1];
    renderizarResumo();
    renderizarChuva();
    renderizarRio();
  }

  async function iniciar() {
    estado.comunidade = await AB.iniciarPagina();
    document.getElementById('intro').textContent = t('pd.intro', { lugar: estado.comunidade.nome });
    AB.configurarOuvir(textoParaOuvir);
    await carregarDados();
    setInterval(carregarDados, ATUALIZAR_A_CADA_MS);
  }

  iniciar();
})();
