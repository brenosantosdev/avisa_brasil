"""Converte a setorização de risco geológico do SGB (shapefile) em GeoJSON para o mapa.

Fonte: SOUZA, A. G. H. de; OLIVEIRA, M. A. de. Setorização de áreas de risco geológico:
São Gabriel da Cachoeira. Belo Horizonte: CPRM/SGB, 2022.
https://rigeo.sgb.gov.br/handle/doc/22730 (arquivo sig_saogabrieldacachoeira_am_risco_2022.zip)

Uso (sem bibliotecas extras):
    python ferramentas/sgb_para_geojson.py <pasta>/SIG/shp/AM_SAOGABR_SR_CPRM <saida.geojson> [--listar]

O shapefile está em SIRGAS 2000 (graus), que para um mapa web equivale ao WGS84:
as coordenadas são copiadas sem reprojeção, só arredondadas a 6 casas (~10 cm).
"""
import json
import struct
import sys

# Campos do SGB mantidos no GeoJSON (nome no mapa: nome no shapefile)
CAMPOS = {
    "setor": "cd_setor",
    "local": "LOCAL",
    "dataVistoria": "DATA_SETOR",
    "tipologia": "TIPOLOGIA",
    "grauRisco": "GRAU_RISCO",
    "grauVulnerabilidade": "GRAU_VULNE",
    "moradiasEstimadas": "NUM_EDIF",
    "pessoasEstimadas": "NUM_PESS",
    "descricao": "DESCRICAO",
    "ocupacao": "OBS_OCUP",
    "intervencoesSugeridas": "SUG_INTERV",
}


def ler_dbf(caminho, codificacao):
    with open(caminho, "rb") as f:
        cabecalho = f.read(32)
        registros, tamanho_cabecalho, tamanho_registro = struct.unpack("<xxxxIHH20x", cabecalho)
        campos = []
        while True:
            descritor = f.read(32)
            if descritor[0] == 0x0D:
                break
            nome = descritor[:11].split(b"\0")[0].decode("ascii")
            campos.append((nome, descritor[11:12].decode(), descritor[16]))
        f.seek(tamanho_cabecalho)
        linhas = []
        for _ in range(registros):
            bruto = f.read(tamanho_registro)
            if bruto[:1] == b"*":  # registro apagado
                linhas.append(None)
                continue
            pos = 1
            linha = {}
            for nome, tipo, tamanho in campos:
                valor = bruto[pos:pos + tamanho].decode(codificacao, errors="replace").strip()
                pos += tamanho
                if tipo in "NF" and valor:
                    valor = float(valor) if "." in valor else int(valor)
                linha[nome] = valor if valor != "" else None
            linhas.append(linha)
        return campos, linhas


def ler_shp_poligonos(caminho):
    """Lê polígonos (tipo 5) e devolve, para cada registro, a lista de anéis [[lon, lat], ...]."""
    with open(caminho, "rb") as f:
        dados = f.read()
    tipo = struct.unpack("<i", dados[32:36])[0]
    if tipo not in (5, 15, 25):
        raise ValueError("Shapefile não é de polígonos (tipo %d)" % tipo)
    pos = 100
    formas = []
    while pos < len(dados):
        _, tamanho = struct.unpack(">ii", dados[pos:pos + 8])
        conteudo = dados[pos + 8:pos + 8 + tamanho * 2]
        pos += 8 + tamanho * 2
        tipo_registro = struct.unpack("<i", conteudo[:4])[0]
        if tipo_registro == 0:
            formas.append([])
            continue
        num_partes, num_pontos = struct.unpack("<ii", conteudo[36:44])
        partes = list(struct.unpack("<%di" % num_partes, conteudo[44:44 + 4 * num_partes]))
        inicio = 44 + 4 * num_partes
        pontos = [struct.unpack("<dd", conteudo[inicio + 16 * i:inicio + 16 * (i + 1)]) for i in range(num_pontos)]
        aneis = []
        for i, p in enumerate(partes):
            fim = partes[i + 1] if i + 1 < len(partes) else num_pontos
            aneis.append([[round(x, 6), round(y, 6)] for x, y in pontos[p:fim]])
        formas.append(aneis)
    return formas


def area_assinada(anel):
    return sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(anel, anel[1:])) / 2


def para_geometria(aneis):
    """No shapefile, anel externo é horário (área negativa) e furos são anti-horários."""
    poligonos = []
    for anel in aneis:
        if area_assinada(anel) <= 0 or not poligonos:
            poligonos.append([list(reversed(anel))])  # GeoJSON: externo anti-horário
        else:
            poligonos[-1].append(list(reversed(anel)))
    if len(poligonos) == 1:
        return {"type": "Polygon", "coordinates": poligonos[0]}
    return {"type": "MultiPolygon", "coordinates": poligonos}


def main():
    base, saida = sys.argv[1], sys.argv[2]
    try:
        codificacao = open(base + ".cpg").read().strip() or "latin-1"
    except FileNotFoundError:
        codificacao = "latin-1"
    codificacao = {"UTF-8": "utf-8", "UTF8": "utf-8"}.get(codificacao.upper(), codificacao)
    campos, linhas = ler_dbf(base + ".dbf", codificacao)
    formas = ler_shp_poligonos(base + ".shp")

    if "--listar" in sys.argv:
        print("Campos:", [c[0] for c in campos])
        for linha in linhas:
            print({k: (v[:80] if isinstance(v, str) else v) for k, v in (linha or {}).items()})
        return

    feicoes = []
    for linha, aneis in zip(linhas, formas):
        if linha is None or not aneis:
            continue
        propriedades = {novo: linha.get(original) for novo, original in CAMPOS.items()}
        feicoes.append({"type": "Feature", "properties": propriedades, "geometry": para_geometria(aneis)})

    resultado = {
        "type": "FeatureCollection",
        "fonte": {
            "nome": "Serviço Geológico do Brasil (SGB/CPRM)",
            "titulo": "Setorização de áreas de risco geológico: São Gabriel da Cachoeira (AM)",
            "autores": "Antônio Gilmar Honorato de Souza; Marco Antônio de Oliveira",
            "publicacao": "2022-05",
            "url": "https://rigeo.sgb.gov.br/handle/doc/22730",
            "arquivo": "sig_saogabrieldacachoeira_am_risco_2022.zip (SIG/shp/AM_SAOGABR_SR_CPRM)",
        },
        "features": feicoes,
    }
    with open(saida, "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, separators=(",", ":"))
    print("%d setores gravados em %s" % (len(feicoes), saida))


if __name__ == "__main__":
    main()
