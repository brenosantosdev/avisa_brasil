# Apresentação do AvisaBrasil para a banca

Três partes: o **roteiro da demo** (cerca de 3 minutos), como **abrir o sistema no celular com HTTPS** por um túnel
gratuito e o **checklist** do que limpar e ligar antes de começar.

> Os tempos do simulador foram medidos num ensaio em 14/11/2026, com as cotas provisórias da estação Curicuriari
> (Atenção de cheia 13,85 m, Alerta 14,17 m, Emergência 14,73 m). Se as cotas mudarem, os tempos mudam um pouco:
> faça um ensaio no dia e depois limpe a simulação.

---

## 1. Roteiro da demo (cerca de 3 minutos)

**Arrumação:**
- **Notebook (projetado):** painel da Defesa Civil em `http://localhost:8080/painel.html`, já com login. O painel ao vivo
  fica no `localhost` porque o túnel rápido da Cloudflare não transmite eventos ao vivo (SSE).
- **Celular da equipe:** o AvisaBrasil aberto pelo endereço do túnel (de preferência instalado como app).
- **Celulares dos avaliadores:** o app **ntfy** inscrito no tópico da demo.
- **Terminal:** o comando do simulador já digitado, sem apertar Enter.

| Tempo | O que fazer | O que falar (sugestão) |
|---|---|---|
| 0:00 | Mostrar o **Início** no celular da equipe: o semáforo real (hoje, "Atenção · Seca"). Apertar **Ouvir**. | "Quem mora na beira do Rio Negro quer saber três coisas: estou seguro, o que está acontecendo e o que eu faço agora. Isto é o nível medido hoje pela ANA, e o celular lê em voz alta." |
| 0:25 | Trocar o idioma no topo para **Español** e voltar. Mostrar o botão do **VLibras**. | "Português, espanhol e inglês, com Libras. Não inventamos traduções em línguas indígenas: a estrutura está pronta para quando houver tradução validada por falantes nativos." |
| 0:40 | No notebook, mostrar o **painel**: situação ao vivo, sensores, canais ligados. Abrir rapidamente **Próximos dias** (chuva em baldes e vazão do GloFAS). | "Tudo aqui é dado real de hoje: chuva do Open-Meteo, vazão do GloFAS, régua da ANA e avisos do INMET. O único dado simulado é o sensor da comunidade, e a tela avisa isso." |
| 1:00 | No terminal, apertar Enter em `java simulador/Simulador.java cheia --passos 20`. | "Não temos sensor físico, então o simulador faz o rio subir, uma leitura a cada 2 segundos, e cada leitura vale 1 hora." |
| ~1:22 | O painel muda para **ALERTA – CHEIA** sem recarregar. A notificação chega nos celulares (ntfy). | "O motor de risco viu que, nessa velocidade, o rio chega à cota de Alerta em poucas horas e subiu o nível antes. O aviso chegou agora no celular de vocês." |
| ~1:35 | O nível vai para **EMERGÊNCIA – CHEIA**, e chega a segunda notificação. Atualizar o Início no celular da equipe: vermelho, com "O que eu faço agora?". | "A mensagem é curta, diz o que fazer e vem marcada como SIMULAÇÃO, para ninguém confundir com um alerta real." |
| 2:00 | Pedir a um avaliador para abrir o link (QR code) e tocar **Preciso de ajuda** (3 pessoas, com localização). | "Quem precisa de ajuda aperta um botão. Se estiver sem internet, o pedido fica guardado no celular e sai sozinho quando a conexão volta." |
| 2:15 | No painel, o pedido aparece **na lista e no mapa**, com aviso sonoro. Clicar **Marcar como atendido**. | "A Defesa Civil vê na hora quem pediu, quantas pessoas são e onde estão." |
| 2:30 | No celular da equipe, ligar o **modo avião** e abrir o Início e o **Entenda os riscos**. | "Instalado, o app abre sem internet com o último dado salvo e avisa de quando é esse dado." |
| 2:50 | Fechamento. | "AvisaBrasil: dado real, linguagem simples, voz e o que fazer, para quem os alertas oficiais não alcançam. ODS 13, 10, 11 e 9." |

**Se algo falhar (plano B):**
- **O ntfy não chega:** mostrar o alerta em **Alertas → Últimas alertas** no painel. O canal "Registro do servidor"
  sempre registra o alerta.
- **A internet do local cair:** o notebook continua funcionando em `localhost`, com os dados em cache e o aviso
  "desatualizado". Mostrar o vídeo de backup da demo.
- **O túnel não abrir:** usar o celular da equipe no mesmo Wi-Fi não resolve, porque sem HTTPS não há instalação nem
  localização. Mostrar no notebook com a janela no tamanho de celular (F12 → modo dispositivo).

---

## 2. Abrir no celular com HTTPS (túnel gratuito)

**Por que HTTPS:** sem HTTPS (endereço `https://`), o celular não instala o app, não guarda as telas para usar sem
internet e não deixa enviar a localização no "Preciso de ajuda". Um endereço `http://192.168...` da rede local
não basta. O túnel cria um endereço `https://` público que leva ao `localhost:8080` do notebook.

### Opção A: Cloudflare Tunnel, "quick tunnel" (recomendado, sem conta)

```powershell
# instalar uma vez
winget install --id Cloudflare.cloudflared

# com o servidor já rodando, em outro terminal
cloudflared tunnel --url http://localhost:8080
```

O terminal mostra um endereço como `https://palavras-aleatorias.trycloudflare.com`. Para os avaliadores, gere um
QR code desse endereço: no Chrome, abra o endereço, depois Compartilhar → Código QR.

Limites do quick tunnel (documentação da Cloudflare):
- **Sem eventos ao vivo (SSE).** Por isso o painel fica aberto em `localhost` no notebook. As telas do celular
  (Início, Preciso de ajuda, Entenda os riscos) não usam SSE.
- **Endereço novo a cada vez.** Um app instalado num endereço antigo não abre no novo, então instale **no dia**,
  depois de abrir o túnel.
- Até 200 pedidos simultâneos e nenhuma garantia de disponibilidade. Para uma demo, é suficiente.

### Opção B: ngrok (precisa de conta gratuita)

```powershell
winget install ngrok.ngrok
ngrok config add-authtoken SEU_TOKEN    # o token fica no painel do ngrok; nunca coloque no Git
ngrok http 8080
```

No plano gratuito, cada navegador vê **uma página de aviso do ngrok** na primeira visita. É só tocar em
"Visit Site". Isso atrasa a demo, então prefira a Opção A.

### Cuidados com o túnel aberto

Enquanto o túnel está aberto, **qualquer pessoa com o link** acessa o sistema:
- Defina **`PAINEL_SENHA`** com uma senha forte. Sem ela, o painel fica trancado.
- Defina **`SENSOR_API_KEY`**. Sem ela, qualquer pessoa poderia mandar leituras falsas e disparar alertas.
- **Não** ligue o console do H2 (`H2_CONSOLE`): ele vem desligado por padrão.
- Divulgue o link só na sala e feche o túnel (`Ctrl + C`) logo depois da apresentação.
- O sistema lê o IP real de cada celular que passa pelo túnel (`server.forward-headers-strategy=native`). Assim, o
  limite de envios do "Preciso de ajuda" (20 a cada 10 minutos) vale por endereço de internet, e não para todos
  juntos. Celulares no mesmo Wi-Fi saem pelo mesmo endereço e dividem esse limite, mas na demo isso não pesa.

---

## 3. Checklist antes de apresentar

### Na véspera
- [ ] `cd backend` → `.\mvnw.cmd test`: todos os testes passando.
- [ ] `cloudflared` instalado e testado uma vez num celular **com 4G** (fora do Wi-Fi do notebook).
- [ ] App **ntfy** instalado nos celulares que vão receber o alerta e inscrito no tópico (`NTFY_TOPICO`). Pode ser
      o celular da equipe. Num avaliador, só se ele quiser instalar.
- [ ] Ensaio completo do roteiro, cronometrado, e **limpeza da simulação depois do ensaio**.
- [ ] **Vídeo de backup** da demo gravado (plano B) e capturas de tela no slide.

### No dia, 30 minutos antes
1. [ ] **Variáveis** no PowerShell onde o servidor vai rodar (nunca em arquivo versionado):
   ```powershell
   $env:PAINEL_SENHA   = "senha-longa-da-demo"
   $env:SENSOR_API_KEY = "chave-longa-do-sensor"
   $env:NTFY_TOPICO    = "avisabrasil-sgc-xxxxxxxx"
   $env:ANA_USUARIO    = "..."   # e ANA_SENHA
   # opcionais: TELEGRAM_BOT_TOKEN, TWILIO_*
   ```
2. [ ] **Subir o servidor pelo menos 15 minutos antes** (`cd backend` → `.\mvnw.cmd spring-boot:run`), para buscar
      Open-Meteo, GloFAS, ANA, INMET e OpenStreetMap. **Não reinicie várias vezes:** a ANA bloqueia logins
      repetidos (o sistema espera no mínimo 10 minutos entre logins e reaproveita o token).
3. [ ] Conferir as telas: Início, O rio agora e Próximos dias mostram "Atualizado hoje", sem faixa "Desatualizado".
4. [ ] **Painel** (`localhost:8080/painel.html`): entrar; em "Canais de envio", o ntfy aparece "Ligado"; tocar
      **Enviar mensagem de teste** e conferir que chegou no celular.
5. [ ] **Limpar:** no painel, marcar **"Também apagar todos os pedidos de ajuda e respostas"** e tocar duas vezes
      em **Limpar dados simulados**. Conferir:
      - [ ] o Início não tem a faixa "SIMULAÇÃO";
      - [ ] "Últimas alertas" e "Pedidos de ajuda" estão vazios;
      - [ ] o nível mostrado é o real.
6. [ ] **Simulador** num terminal na pasta do projeto, com a mesma chave:
   ```powershell
   $env:SENSOR_API_KEY = "chave-longa-do-sensor"
   java simulador/Simulador.java cheia --passos 20    # digitado, sem apertar Enter
   ```
7. [ ] **Túnel:** `cloudflared tunnel --url http://localhost:8080` → copiar o endereço → QR code no slide ou na tela.
8. [ ] **Celular da equipe:**
   - [ ] abrir o endereço do túnel;
   - [ ] **instalar** (cartão "Instalar no celular");
   - [ ] permitir a localização;
   - [ ] abrir Início, Preciso de ajuda e Entenda os riscos uma vez, para ficarem salvos para o modo sem internet;
   - [ ] idioma em **Português**;
   - [ ] volume alto e "Não perturbe" desligado.
9. [ ] **Notebook:**
   - [ ] painel em tela cheia, "Ao vivo" aceso;
   - [ ] notificações do Windows desligadas;
   - [ ] outras abas fechadas;
   - [ ] carregador ligado.

### Depois da apresentação
- [ ] `Ctrl + C` no túnel: o link para de funcionar.
- [ ] No painel, **Limpar dados simulados** com a opção dos pedidos marcada, para apagar os pedidos dos avaliadores.
- [ ] `Ctrl + C` no servidor (encerramento normal).

> Lembretes: a simulação continua contando por 30 minutos depois da última leitura, e o alerta só é enviado quando o
> nível **sobe**. Para repetir a demo, limpe a simulação antes. Senão, o nível já estará em Emergência e nenhum
> alerta novo será enviado.
