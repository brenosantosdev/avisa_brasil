# AvisaBrasil

Rede de alerta de cheias e secas para comunidades ribeirinhas e indígenas, com linguagem simples, voz e orientação do que fazer.
Projeto da **Global Solution 2026.2 da FIAP** (tema CitizenTech). Território piloto: **São Gabriel da Cachoeira (AM), Rio Negro**.

> Estado atual: **etapa 4**. As telas **O rio agora** (nível medido pela ANA), **Próximos dias** (chuva e vazão do
> Rio Negro) e **Mapa** (pontos de apoio do OpenStreetMap e setores de risco oficiais do SGB) já usam dados reais.

## Requisitos

- **Java 17 ou superior** (testado com Java 21). Confira com `java -version`.
- **Não é preciso instalar o Maven**: o projeto usa o Maven Wrapper (`mvnw.cmd`), que baixa a versão certa sozinho na primeira execução.

## Como rodar no Windows

Abra o **PowerShell** (ou o Prompt de Comando) na pasta do projeto:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

No Prompt de Comando (cmd) o comando é `mvnw.cmd spring-boot:run`, sem o `.\`.

Quando aparecer `Started AvisabrasilApplication`, abra no navegador:

| O quê | Endereço |
|---|---|
| Página inicial | http://localhost:8080 |
| **O rio agora** (nível da ANA, régua e histórico) | http://localhost:8080/rio-agora.html |
| **Próximos dias** (chuva e rio, dados reais) | http://localhost:8080/proximos-dias.html |
| **Mapa** (pontos de apoio e setores de risco) | http://localhost:8080/mapa.html |
| **Painel da Defesa Civil** (cadastro de pontos seguros) | http://localhost:8080/painel.html |
| Swagger UI (testar a API) | http://localhost:8080/swagger-ui.html |
| Previsão do tempo (JSON) | http://localhost:8080/api/previsao/tempo |
| Previsão do rio (JSON) | http://localhost:8080/api/previsao/rio |
| Rio agora (JSON) | http://localhost:8080/api/rio/agora |
| Status do sistema | http://localhost:8080/api/status |
| Console do banco H2 | http://localhost:8080/h2-console |
| Saúde (Actuator) | http://localhost:8080/actuator/health |

Para parar, pressione `Ctrl + C` no terminal.

**Console do H2:** JDBC URL `jdbc:h2:file:./data/avisabrasil;AUTO_SERVER=TRUE`, usuário `sa`, senha em branco.
O arquivo do banco fica em `backend/data/` e não vai para o Git.

**Porta ocupada?** Rode em outra porta: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`

## Fontes de dados

| Fonte | O que traz | Atualização |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | Chuva, chance de chuva e temperatura dos próximos 7 dias | a cada 1 hora |
| [GloFAS / Copernicus](https://open-meteo.com/en/docs/flood-api) via Open-Meteo | Vazão do rio: últimos 30 dias e previsão de 30 dias | a cada 6 horas |
| GloFAS, série desde 1997 | O que é "normal" para o rio em cada época do ano | 1 vez por dia |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) via Overpass API | Escolas, unidades de saúde, prédios públicos e abrigos num raio de 8 km | 1 vez por dia |
| [SGB/CPRM: Setorização de risco de São Gabriel da Cachoeira (2022)](https://rigeo.sgb.gov.br/handle/doc/22730) | 14 setores oficiais de risco alto e muito alto | arquivo fixo |
| [ANA HidroWebService](https://www.ana.gov.br/hidrowebservice/swagger-ui/index.html), telemetria | Nível (cota) e vazão do Rio Negro a cada 15 min na estação 14330000 (Curicuriari) | a cada 15 min |
| ANA HidroWebService, régua convencional | Histórico diário das estações 14330000 (desde 1977) e 14320001 (cidade, desde 1982) | baixado ano a ano; ano atual 1 vez por dia |

### ANA: estações e cotas

- **Estações** escolhidas pelo inventário `HidroInventarioEstacoes` (UF=AM), sem códigos inventados. A régua da
  cidade (14320001) não tem mais telemetria (encerrada em 2017) e suas leituras manuais aparecem na ANA com meses de
  atraso; por isso o nível atual vem de **Curicuriari (14330000)**, a única estação do Rio Negro na região com
  telemetria ativa, cerca de 33 km rio abaixo. A régua da cidade entra no histórico ("Já aconteceu antes?").
- **Credenciais:** só `ANA_USUARIO` e `ANA_SENHA` (variáveis de ambiente). Sem elas a integração fica desligada.
- **Token:** reaproveitado por 55 min (a ANA dá 60). Nunca há login antes de 10 min da tentativa anterior, nem depois
  de reiniciar: a sessão (token e horários, nunca usuário/senha) fica em `backend/data/cache/ana-sessao.json`, fora do Git.
  Token e credenciais nunca vão para o log.
- **Cotas de Atenção, Alerta e Emergência:** não encontramos cota oficial do SGB/SACE para estas estações (11/2026).
  Enquanto não houver, a tela mostra **cotas provisórias**, calculadas pelas maiores cheias anuais da ANA
  (Atenção = mediana; Alerta = percentil 80; Emergência = percentil 95). Para usar cotas oficiais, preencha
  `avisabrasil.ana.cotas.*` em `application.properties` **com a fonte** em `avisabrasil.ana.cotas.fonte`.
- **ANA × GloFAS:** a tela compara a medição com o modelo. Se discordarem sobre seca ou cheia, vale a ANA (também
  no resumo de "Próximos dias").

- **Cache:** a última resposta válida de cada fonte fica em memória e em `backend/data/cache/`. Se a internet ou a API cair,
  a tela continua mostrando o último dado válido com o aviso **"Desatualizado"**. Depois de uma falha, o sistema
  tenta de novo a cada 5 minutos.
- **Ponto do rio:** o GloFAS usa uma grade de ~5 km. Na coordenada exata da sede (-0,1294 / -67,0917) a grade
  pega um igarapé (vazão < 1 m³/s). Por isso a vazão é lida no ponto -0,17 / -67,08, que cai no leito do Rio Negro
  (~4.000 m³/s). Os dois pontos estão em `application.properties`.
- **GloFAS é modelo, não medição.** A tela diz isso. A medição oficial está em "O rio agora".
  O "normal da época" usa a própria série do GloFAS (1997 até o ano anterior), com 7 dias antes e 7 depois da data em
  cada ano. Os maiores e menores valores da série são do modelo; os recordes oficiais devem ser conferidos com a ANA.
- **Pontos de apoio do OSM não são abrigos oficiais.** Em 11/2026 o OSM tinha só 13 pontos úteis no raio de 8 km
  (vários sem nome). A tela mostra só os reais e avisa isso. Abrigos confirmados são cadastrados pela Defesa Civil no
  painel e aparecem em verde, antes dos outros.
- **Setores de risco:** só aparecem porque existe fonte oficial (SGB, vistoria de março/abril de 2022). O GeoJSON em
  `backend/src/main/resources/static/dados/` foi gerado do shapefile oficial com `ferramentas/sgb_para_geojson.py`
  (sem reprojeção: SIRGAS 2000 ≈ WGS84). Pontos que caem dentro de um setor são marcados no mapa e no painel.
- **Overpass:** uma consulta por dia, com servidor reserva. Em horário de pico a fila pode passar de 30 s, por isso o
  tempo limite dessa fonte é 90 s.
- **Painel sem senha (por enquanto):** qualquer pessoa com o endereço consegue cadastrar ou remover pontos. O acesso
  restrito precisa entrar antes de usar fora da demonstração.

## Testes

```powershell
cd backend
.\mvnw.cmd test
```

## Outros bancos (profiles)

O padrão é H2 em arquivo. Para PostgreSQL ou Oracle, defina `DB_URL`, `DB_USUARIO` e `DB_SENHA` e ative o profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=oracle"
```

## Variáveis de ambiente (segredos)

Nenhuma senha ou token fica no código. Os nomes das variáveis estão em [`.env.example`](.env.example).
Cada integração (ANA, ntfy, Telegram, Twilio) fica desligada automaticamente quando a sua variável não existe.

Para definir uma variável só na sessão atual do PowerShell:

```powershell
$env:NTFY_TOPICO = "meu-topico"
.\mvnw.cmd spring-boot:run
```

## Estrutura

```
avisabrasil/
├── backend/        Spring Boot (API REST, front-end em src/main/resources/static)
├── ferramentas/    scripts de apoio (conversão da setorização do SGB para GeoJSON)
├── simulador/      simulador do sensor de nível (etapa 5)
└── firmware/       ESP32 para o Wokwi (etapa 11)
```

## Autor

Breno Santos, Sistemas de Informação, FIAP.
