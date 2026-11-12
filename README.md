# AvisaBrasil

Rede de alerta de cheias e secas para comunidades ribeirinhas e indígenas, com linguagem simples, voz e orientação do que fazer.
Projeto da **Global Solution 2026.2 da FIAP** (tema CitizenTech). Território piloto: **São Gabriel da Cachoeira (AM), Rio Negro**.

> Estado atual: **etapa 2**. A tela **Próximos dias** já mostra chuva (Open-Meteo) e vazão do Rio Negro (GloFAS) com dados reais.

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
| **Próximos dias** (chuva e rio, dados reais) | http://localhost:8080/proximos-dias.html |
| Swagger UI (testar a API) | http://localhost:8080/swagger-ui.html |
| Previsão do tempo (JSON) | http://localhost:8080/api/previsao/tempo |
| Previsão do rio (JSON) | http://localhost:8080/api/previsao/rio |
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

- **Cache:** a última resposta válida de cada fonte fica em memória e em `backend/data/cache/`. Se a internet ou a API cair,
  a tela continua mostrando o último dado válido com o aviso **"Desatualizado"**. Depois de uma falha, o sistema
  tenta de novo a cada 5 minutos.
- **Ponto do rio:** o GloFAS usa uma grade de ~5 km. Na coordenada exata da sede (-0,1294 / -67,0917) a grade
  pega um igarapé (vazão < 1 m³/s). Por isso a vazão é lida no ponto -0,17 / -67,08, que cai no leito do Rio Negro
  (~4.000 m³/s). Os dois pontos estão em `application.properties`.
- **GloFAS é modelo, não medição.** A tela diz isso. A medição oficial (régua da ANA) entra na etapa 4.

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
├── simulador/      simulador do sensor de nível (etapa 5)
└── firmware/       ESP32 para o Wokwi (etapa 11)
```

## Autor

Breno Santos, Sistemas de Informação, FIAP.
