# AvisaBrasil

Rede de alerta de cheias e secas para comunidades ribeirinhas e indígenas, com linguagem simples, voz e orientação do que fazer.
Projeto da **Global Solution 2026.2 da FIAP** (tema CitizenTech). Território piloto: **São Gabriel da Cachoeira (AM), Rio Negro**.

> Estado atual: **etapa 1** (projeto Spring Boot + Swagger + H2). As telas e integrações com dados reais chegam nas próximas etapas.

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
| Swagger UI (testar a API) | http://localhost:8080/swagger-ui.html |
| Status do sistema | http://localhost:8080/api/status |
| Console do banco H2 | http://localhost:8080/h2-console |
| Saúde (Actuator) | http://localhost:8080/actuator/health |

Para parar, pressione `Ctrl + C` no terminal.

**Console do H2:** JDBC URL `jdbc:h2:file:./data/avisabrasil;AUTO_SERVER=TRUE`, usuário `sa`, senha em branco.
O arquivo do banco fica em `backend/data/` e não vai para o Git.

**Porta ocupada?** Rode em outra porta: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`

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
