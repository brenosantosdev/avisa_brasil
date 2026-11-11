# AvisaBrasil — instruções para o Claude Code

Este arquivo dá ao Claude Code o contexto do projeto. Leia tudo antes de começar e siga a ordem de construção da seção 9.

## 1. O projeto

**AvisaBrasil** é o projeto da Global Solution 2026.2 da FIAP (tema CitizenTech). É uma rede de alerta de cheias e secas para comunidades com pouco acesso à tecnologia. O aviso precisa chegar a quem os canais oficiais não alcançam, em linguagem simples, com voz e dizendo o que fazer.

- **Cidadão:** famílias ribeirinhas e indígenas (pescadores, agricultores, professores, idosos e crianças que dependem do rio).
- **Problema:** os alertas oficiais dependem de sinal de celular, de smartphone e de leitura em português.
- **Território piloto:** São Gabriel da Cachoeira (AM), Rio Negro. Coordenadas aproximadas: lat -0.13, lon -67.09 (confirmar no OpenStreetMap).
- **ODS:** 13 (principal), 10, 11 e 9.
- **Autor:** Breno Santos, estudante de Sistemas de Informação da FIAP.

O diferencial do projeto é **funcionar de verdade, com dados reais**. Nada de dados inventados apresentados como reais. Quando algo for de exemplo ou simulado, a tela precisa dizer isso.

## 2. Stack

- **Java 17+** com **Spring Boot 3**, Maven com **Maven Wrapper** (`mvnw` / `mvnw.cmd`). O computador do autor é **Windows**.
- **Banco:** H2 em arquivo no desenvolvimento; profiles prontos para PostgreSQL e Oracle.
- **API REST** documentada com **springdoc-openapi** (Swagger UI em `/swagger-ui.html`), para testar sem Postman.
- **Front-end:** HTML, CSS e JavaScript servidos pelo Spring (`src/main/resources/static`). Sem framework pesado.
- **Mapa:** Leaflet + tiles do OpenStreetMap (com a atribuição obrigatória "© OpenStreetMap contributors").
- **Gráficos:** Chart.js.
- **Voz:** Web Speech API do navegador (`speechSynthesis`), no idioma escolhido.
- **Tempo real no painel:** Server-Sent Events (SSE).
- **Sensor:** simulador em Java + firmware ESP32 para o Wokwi. Não há hardware físico.

## 3. Fontes de dados reais

Toda informação exibida mostra **a fonte e o horário da última atualização**. Faça cache das respostas e trate erros: se uma API cair, mostre o último dado válido com o aviso "desatualizado".

| Fonte | Uso | Acesso |
|---|---|---|
| **Open-Meteo Forecast** `https://api.open-meteo.com/v1/forecast` | Chuva, temperatura e chance de chuva (horário e diário). Use `timezone=America/Manaus` | Grátis, sem chave. Atualizar a cada 1 hora |
| **Open-Meteo Flood (GloFAS)** `https://flood-api.open-meteo.com/v1/flood?latitude=..&longitude=..&daily=river_discharge` | Vazão prevista do rio para até 30 dias | Grátis, sem chave. Atualizar a cada 6 horas |
| **ANA HidroWebService** (Swagger: `https://www.ana.gov.br/hidrowebservice/swagger-ui/index.html`) | Nível (cota), vazão e chuva das estações telemétricas. Endpoints: `HidroInventarioEstacoes/v1` (descobrir estações de São Gabriel da Cachoeira), `HidroinfoanaSerieTelemetricaAdotada/v1` (série) | Acesso já liberado para o autor. Autenticação na rota OAuth (`/EstacoesTelemetricas/OAUth/v1`); confira no Swagger como enviar usuário e senha. **O token vale 60 minutos: guarde e reutilize. Logins repetidos podem bloquear o IP.** Atualizar a cada 15 minutos |
| **INMET avisos** RSS `https://apiprevmet3.inmet.gov.br/avisos/rss` | Avisos meteorológicos oficiais | Grátis |
| **OpenStreetMap / Overpass** | Mapa e pontos seguros reais (escolas, postos de saúde) | Grátis; respeitar a política de uso |
| **ntfy.sh** | Notificação push no celular (demo ao vivo) | Grátis, sem cadastro |
| **Telegram Bot API** | Alerta em texto e áudio para grupos | Grátis |
| **Twilio** | SMS com resposta 1 = estou bem / 2 = preciso de ajuda | Conta trial (só números verificados) |

Não invente o código da estação da ANA: descubra pelo endpoint de inventário.

## 4. Segredos (muito importante)

- **Nunca** coloque a senha da ANA, tokens do Twilio/Telegram ou qualquer credencial no código ou em arquivos versionados.
- Leia de **variáveis de ambiente**: `ANA_USUARIO`, `ANA_SENHA`, `TELEGRAM_BOT_TOKEN`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM`, `NTFY_TOPICO`.
- Crie um `.env.example` só com os nomes das variáveis e garanta `.env`, `*.db` e `target/` no `.gitignore`.
- Cada integração opcional (Twilio, Telegram, ntfy, ANA) deve ser desligada automaticamente quando a variável não existir, sem quebrar o sistema.

## 5. Motor de risco

Níveis: **Normal**, **Atenção**, **Alerta**, **Emergência**.

Entradas:
1. Nível medido pelo sensor (simulado) e a velocidade de subida (cm/h, por regressão linear das últimas leituras).
2. Nível oficial da estação da ANA.
3. Chuva prevista (Open-Meteo).
4. Vazão prevista (GloFAS).

Regras:
- Cada sensor/estação tem cotas configuráveis de Atenção, Alerta e Emergência.
- Calcule "em quanto tempo chega ao próximo nível" pela tendência.
- Suba o nível de forma preventiva quando a previsão indicar que o próximo nível será atingido em poucas horas (valor configurável).
- Só dispare um novo alerta quando o nível **subir**. Quando voltar ao Normal, envie "situação normalizada".
- Comece com regras simples e explicáveis. Modelo de IA treinado com o histórico da ANA é visão de futuro.

## 6. Telas

### Princípios de design (para quem tem pouca familiaridade com tecnologia)
- Sempre **ícone + cor + palavra + som**, nunca só cor.
- A tela inicial responde a 3 perguntas: **Estou seguro? O que está acontecendo? O que eu faço agora?**
- Botões grandes (mínimo 44 px), texto base 18 px, fonte **Atkinson Hyperlegible** (Google Fonts).
- Botão **"Ouvir"** em toda tela.
- Paleta: azul do rio `#0B4F73`, fundo `#F3F6F5`, texto `#10262E`. Semáforo: Normal `#1E7A46`, Atenção `#F2C230` (texto escuro), Alerta `#C2541A`, Emergência `#A3221B`.
- Todo número vem explicado com uma comparação do dia a dia:
  - "Cota 11,60 m" → "Faltam 40 cm para o perigo, mais ou menos a altura de um joelho"
  - "45 mm de chuva" → "Chuva forte: mais de 2 baldes por metro quadrado" (1 mm = 1 litro por m²; um balde grande tem uns 20 litros)
  - Horários em linguagem natural ("amanhã à tarde"), nunca UTC

### Páginas
1. **Início:** semáforo grande da comunidade, as 3 perguntas, legenda dos 4 níveis, checklist "O que fazer agora" por nível e mochila de emergência.
2. **O rio agora:** nível atual × nível de perigo, tendência, régua ilustrada com um bonequinho e uma casa de palafita, gráfico dos últimos dias e comparação com cheias passadas (dados da ANA).
3. **Próximos dias:** chuva por dia explicada em baldes e previsão do rio (GloFAS).
4. **Mapa:** mapa real (Leaflet + OSM) com a localização da comunidade, pontos seguros reais, área de risco, pedidos de ajuda e caminho até o ponto seguro.
5. **Entenda os riscos:** cheia, seca, calor extremo e deslizamento (o que é, sinais de perigo, como se proteger).
6. **Preciso de ajuda:** "Estou bem" / "Preciso de ajuda" com o número de pessoas, fila offline (localStorage, envia quando voltar a conexão), ligar para 199, 193 e 192 e aviso pronto para o WhatsApp.
7. **Painel da Defesa Civil:** sensores, gráficos, alertas enviados, pedidos de ajuda (marcar como atendido) e disparo de alerta manual.

Referência visual: o protótipo do projeto (versão web e celular) já define layout, cores e textos. Siga o mesmo padrão.

### Idiomas
- **Português, espanhol e inglês** completos, com arquivos de tradução separados (por exemplo `i18n/pt.json`, `es.json`, `en.json`) e um seletor no topo.
- **Libras:** integrar o widget **VLibras** (gov.br).
- **Não** invente traduções para línguas indígenas. A estrutura deve permitir adicioná-las depois, quando houver tradução validada por falantes nativos.

## 7. Estrutura sugerida

```
avisabrasil/
├── CLAUDE.md
├── README.md               como rodar no Windows, variáveis, demo
├── .env.example
├── backend/
│   ├── pom.xml + mvnw/mvnw.cmd
│   └── src/main/java/br/com/avisabrasil/
│       ├── dominio/        Sensor, Leitura, Alerta, PedidoAjuda, Comunidade, PontoSeguro
│       ├── repositorio/
│       ├── integracoes/    OpenMeteoClient, GlofasClient, AnaClient, InmetClient, OverpassClient
│       ├── risco/          MotorDeRisco, Previsao
│       ├── notificacao/    Notificador (interface), Ntfy, Telegram, TwilioSms, Log
│       ├── agendador/      tarefas agendadas de atualização
│       └── web/            controllers REST, SSE
│   └── src/main/resources/static/   páginas, css, js, i18n
├── simulador/              Simulador.java (cheia, seca, normal)
└── firmware/               ESP32 (.ino) + diagram.json do Wokwi
```

## 8. Qualidade

- Testes unitários para o motor de risco, a previsão por tendência e o parse das respostas das APIs (com JSON de exemplo salvo em `src/test/resources`).
- Teste de integração da API com MockMvc.
- Código e comentários em português.
- Commits pequenos e com mensagens claras.

## 9. Ordem de construção

1. Projeto Spring Boot rodando + Swagger + H2 + README com os comandos para Windows (`mvnw.cmd spring-boot:run`).
2. Integração **Open-Meteo** (chuva/clima) e **GloFAS** (vazão), com cache e tela "Próximos dias".
3. **Mapa real** com Leaflet + OSM e pontos seguros reais via Overpass.
4. Integração **ANA** (descobrir a estação, autenticar com token reaproveitado, ler a série) e tela "O rio agora".
5. **Motor de risco** + **simulador do sensor** + tela Início com o semáforo.
6. **Alertas**: ntfy primeiro (demo ao vivo), depois Telegram e Twilio.
7. "Preciso de ajuda" com fila offline + Painel da Defesa Civil com SSE.
8. Idiomas (PT, ES, EN) + VLibras + botão Ouvir.
9. INMET + "Entenda os riscos".
10. Versão para celular (layout responsivo + PWA instalável e offline).
11. Firmware ESP32 no Wokwi.

Ao terminar cada etapa: rodar os testes, subir o sistema e mostrar ao autor como ver o resultado no navegador.

## 10. Demo para a banca

O painel mostra a chuva e a vazão reais de hoje em São Gabriel da Cachoeira → o simulador faz o rio subir → o motor muda o nível → o alerta chega ao vivo no celular dos avaliadores (ntfy) → alguém aperta "Preciso de ajuda" e o pedido aparece no mapa do painel.

## 11. Fora do escopo do Claude Code

O dossiê da disciplina **Gestão Corporativa com TI** deve ser escrito pelo grupo. A demanda só permite IA para consulta e validação de estrutura, com declaração obrigatória. Não escreva esse texto.
