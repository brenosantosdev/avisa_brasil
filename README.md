# AvisaBrasil

Rede de alerta de cheias e secas para comunidades ribeirinhas e indígenas, com linguagem simples, voz e orientação do que fazer.
Projeto da **Global Solution 2026.2 da FIAP** (tema CitizenTech). Território piloto: **São Gabriel da Cachoeira (AM), Rio Negro**.

> Estado atual: **etapa 8** (português, espanhol e inglês, VLibras e botão Ouvir no idioma escolhido), depois do "Preciso de ajuda" com fila offline e do Painel da Defesa Civil ao vivo. A tela **Início** mostra o semáforo da comunidade (maior risco entre cheia e seca),
> calculado pelo motor de risco com dados reais da ANA, chuva e GloFAS, mais um sensor **simulado**. As telas
> **O rio agora**, **Próximos dias** e **Mapa** também usam dados reais.

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
| **Início** (semáforo e o que fazer) | http://localhost:8080 |
| **O rio agora** (nível da ANA, régua e histórico) | http://localhost:8080/rio-agora.html |
| **Próximos dias** (chuva e rio, dados reais) | http://localhost:8080/proximos-dias.html |
| **Mapa** (pontos de apoio e setores de risco) | http://localhost:8080/mapa.html |
| **Preciso de ajuda** (funciona sem internet) | http://localhost:8080/ajuda.html |
| **Painel da Defesa Civil** (com senha: `PAINEL_SENHA`) | http://localhost:8080/painel.html |
| Swagger UI (testar a API) | http://localhost:8080/swagger-ui.html |
| Previsão do tempo (JSON) | http://localhost:8080/api/previsao/tempo |
| Previsão do rio (JSON) | http://localhost:8080/api/previsao/rio |
| Rio agora (JSON) | http://localhost:8080/api/rio/agora |
| Risco atual e alertas (JSON) | http://localhost:8080/api/risco e http://localhost:8080/api/risco/alertas |
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
  Enquanto não houver, as telas mostram **cotas provisórias**, calculadas pelo histórico da ANA (1978–2025):
  - **Cheia** (maiores cheias de cada ano): Atenção = passa em 1 de cada 2 anos (p50); Alerta = 1 de cada 3 anos (p67);
    Emergência = 1 de cada 10 anos (p90).
  - **Seca** (menores cotas de cada ano): Atenção = fica abaixo em 1 de cada 2 anos (p50); Alerta = 1 de cada 3 anos
    (p33); Emergência = 1 de cada 10 anos (p10). Também é seca quando o rio está entre os 25% / 10% / 5% níveis mais
    baixos para a **época do ano** (Atenção / Alerta / Emergência).
  - Para usar cotas oficiais, preencha `avisabrasil.ana.cotas.*` em `application.properties` **com a fonte**
    (`avisabrasil.ana.cotas.fonte` e `seca-fonte`). Sem fonte o sistema não aceita.
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
- **Painel com senha:** cadastrar/remover pontos seguros, ver e atender pedidos de ajuda, alerta manual, mensagem
  de teste, histórico de envios, limpar a simulação e os eventos ao vivo exigem a senha `PAINEL_SENHA`. Sem a
  variável, o painel fica trancado. O sensor pode exigir chave (`SENSOR_API_KEY`).

## Motor de risco

Níveis **Normal, Atenção, Alerta e Emergência**, de **cheia** ou de **seca**. Vale o maior risco entre todos os fatores:

| Fator | Regra |
|---|---|
| Régua da ANA e sensor | cota acima das cotas de cheia ou abaixo das cotas de seca |
| Seca pela época | cota entre os 25% / 10% / 5% níveis mais baixos para esta época do ano |
| Preventivo | pela tendência (regressão linear), a próxima cota chega em até 24 h (cheia) ou 7 dias (seca): o nível sobe antes |
| Chuva prevista | 50 mm ou mais hoje ou amanhã: Atenção (alagamento e deslizamento) |
| Vazão prevista (GloFAS) | subida rápida (mais de 20% em 7 dias): Atenção |

Em empate, vale a medição oficial (ANA). Alerta é registrado **só quando o nível sobe**; quando volta ao Normal,
registra "situação normalizada". Os limites ficam em `avisabrasil.risco.*`.

## Alertas (ntfy, Telegram, SMS)

Cada alerta do motor de risco vai por todos os canais ligados. A mensagem segue o estilo das telas: nível, o que
está acontecendo, o que fazer agora (3 orientações) e a fonte. Alertas simulados levam **[SIMULAÇÃO]**.
Um alerta nunca é enviado duas vezes pelo mesmo canal, e um alerta com o mesmo conteúdo do anterior não é reenviado
em sequência. O resultado de cada envio aparece no **Painel da Defesa Civil**.

| Canal | Variáveis | Observação |
|---|---|---|
| ntfy (push no celular) | `NTFY_TOPICO` | use um nome difícil de adivinhar |
| Telegram | `TELEGRAM_BOT_TOKEN` | a pessoa envia /start ao bot; responde 1 = estou bem / 2 = preciso de ajuda (long polling, funciona em localhost) |
| SMS (Twilio) | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM`, `TWILIO_DESTINOS` | só envio; na conta trial, só para números verificados |

Sem a variável, o canal fica desligado. No painel há o botão **Enviar mensagem de teste** (não é alerta) e o botão
**Limpar dados simulados** (apaga leituras e alertas simulados antes da demonstração, sem enviar nada). Marcando
**"Também apagar todos os pedidos de ajuda e respostas"**, a limpeza apaga também os pedidos de teste
(`POST /api/simulacao/limpar?pedidos=true`), deixando o banco limpo para a apresentação.

```powershell
# Exemplo no PowerShell (vale só para este terminal)
$env:NTFY_TOPICO = "avisabrasil-sgc-k7q2m9x4"
$env:TELEGRAM_BOT_TOKEN = "<token do BotFather>"
.\mvnw.cmd spring-boot:run
```

## Preciso de ajuda e Painel da Defesa Civil

**Preciso de ajuda** (`/ajuda.html`, público): botões grandes "Estou bem" e "Preciso de ajuda", número de pessoas,
localização (se a pessoa permitir), ligar para 199, 193 e 192 e aviso pronto para o WhatsApp.
- **Fila offline:** o pedido é guardado no celular (localStorage) **antes** de enviar. Sem internet, ele espera e é
  enviado sozinho quando a conexão volta. Cada pedido tem um `idCliente`, então um reenvio não duplica.
- Para a própria tela abrir sem internet é preciso o modo instalável (PWA), que vem na etapa 10.
- Limite de 20 envios por endereço a cada 10 minutos (o endpoint é público).
- **Privacidade (LGPD):** a tela explica em linguagem simples para que servem os dados (só para a Defesa Civil
  ajudar), quem vê (só a equipe, no painel com senha), por quanto tempo ficam guardados e que nome, telefone e
  localização são opcionais. Base legal citada: proteção da vida (LGPD, art. 7º, VII). Os pedidos são apagados
  sozinhos depois do prazo `avisabrasil.ajuda.retencao` (padrão `P90D`, 90 dias; conferido a cada 6 horas).

**Painel da Defesa Civil** (`/painel.html`, com senha):
- **Situação ao vivo** por Server-Sent Events: nível de risco, leituras do sensor (gráfico), novos alertas e novos
  pedidos de ajuda (com aviso sonoro), sem recarregar a página.
- **Pedidos de ajuda** no mapa e na lista, com "Marcar como atendido" e ligação para o telefone informado.
- **Alerta manual** escrito pela Defesa Civil, enviado pelos canais ligados (não muda o nível do motor).
- Canais de alerta, mensagem de teste, histórico de envios, limpar simulação e pontos seguros.

```powershell
$env:PAINEL_SENHA = "uma-senha-longa-e-dificil"
.\mvnw.cmd spring-boot:run
```

Segurança: senha comparada em tempo constante; 5 senhas erradas bloqueiam o endereço por 15 minutos; sessão com
cookie HttpOnly e SameSite=Lax, válida por 8 horas. Para parar o servidor use `Ctrl + C` (encerramento normal).

## Idiomas, Libras e voz

- **Português, espanhol e inglês** completos em `static/i18n/pt.json`, `es.json` e `en.json`. O seletor fica no
  topo de todas as telas e a escolha é guardada no aparelho. Um link pode já abrir no idioma: `/?idioma=es`.
- **Ouvir** em todas as telas, lendo na voz do idioma escolhido (Web Speech API; a voz depende das vozes
  instaladas no aparelho).
- **Libras:** widget **VLibras** (gov.br), o botão azul com as mãos no lado direito da tela. Precisa de internet.
- **Línguas indígenas:** não há tradução inventada. Para acrescentar uma, com tradução validada por falantes
  nativos: criar `i18n/<código>.json` com as mesmas chaves do `pt.json`, pôr o código em `IDIOMAS_DISPONIVEIS`
  (`js/comum.js`) e o nome do idioma na chave `idioma.<código>`. O teste `TraducoesTest` confere se o arquivo
  está completo (mesmas chaves, listas e `{variáveis}`).
- Ficam em português: as mensagens dos alertas enviados (ntfy, Telegram, SMS) e os avisos técnicos do painel
  sobre variáveis de ambiente.

## Simulador do sensor (demonstração)

Não há hardware: o sensor `SGC-01` é **simulado**, e as telas mostram a faixa "SIMULAÇÃO" quando é ele que decide o nível.
Com o servidor rodando, em outro terminal, na pasta do projeto:

```powershell
java simulador/Simulador.java cheia     # sobe a partir de 60 cm abaixo da Atenção de cheia
java simulador/Simulador.java seca      # desce a partir do normal da época
java simulador/Simulador.java normal    # devolve o sensor ao normal
```

Cada leitura vale 1 hora simulada (`--horas-por-passo`), e o padrão é uma leitura a cada 2 s (`--intervalo`) por
40 passos (`--passos`). O sensor deixa de contar 30 minutos depois da última leitura.

Se o servidor tiver `SENSOR_API_KEY`, as leituras precisam do cabeçalho `X-API-Key`; defina a mesma variável no
terminal do simulador. Sem ela, o servidor aceita qualquer leitura e avisa no log (use só em demonstração).

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
├── simulador/      Simulador.java: sensor de nível simulado (cheia, seca, normal)
└── firmware/       ESP32 para o Wokwi (etapa 11)
```

## Autor

Breno Santos, Sistemas de Informação, FIAP.
