# HiveMind-App — App Mobile Nativo (Android · Kotlin)

App Android **nativo em Kotlin** (Jetpack Compose) para **continuar e comandar
os trabalhos do [HiveMind](https://github.com/gilvan-Borges/hivemind-go) pelo
celular**, em tempo real e com o motor 100% no seu PC.

Execução da spec [`docs/SPEC_APP_MOBILE_NATIVO.md`](https://github.com/gilvan-Borges/hivemind-go/blob/main/docs/SPEC_APP_MOBILE_NATIVO.md)
(arquitetura PC Host ↔ App Nativo via HTTP + SSE, com pareamento por QR Code).

## Recursos

- **Projetos:** cards com branch, stack, equipe ativa, badge pulsante de
  aprovações pendentes e contagem de lanes rodando.
- **Chat (Orquestrador):** seletor de lane, streaming ao vivo via SSE,
  **cards de Aprovação** (aprovar/recusar pelo celular), modos
  `Auto · Direto · Planejar · Squad`, anexo de imagem (foto/print),
  **Parar** e **Redirecionar**, histórico de sessões com **Continuar**.
- **Fluxo & Jira:** **Minhas Tasks Jira** (`assignee = currentUser()`) com
  busca e botão **Resolver c/ Equipe**, **Fluxo de PRs** e controle do
  **Auto-Review (Sem Merge)**.
- **Diffs:** lista de arquivos alterados com `+add / -rem` e visualizador
  colorido de diff.
- **Pareamento:** QR Code da IDE (CameraX + ML Kit), modo manual
  (URL + Token), **fallback multi-endpoint** (Tailscale → Wi-Fi), indicador
  de rota ativa e reconexão SSE resiliente (backoff exponencial).
- **Notificações locais** ao receber aprovação pendente ou término de equipe
  + serviço em primeiro plano (`dataSync`) para manter a conexão em segundo
  plano.

## Como usar

1. Na **HiveMind IDE** (PC): clique em **`mobile: off`** na barra de status e
   **ligue o bridge** — o QR Code aparece no modal **App Mobile**.
2. Neste app: **Escanear QR Code da IDE** (ou cole URL + token manualmente).
3. Pronto — as equipes rodando no PC aparecem aqui em tempo real, e as
   aprovações somem dos dois lados na hora.

> O bridge detecta automaticamente o IP **Tailscale** (`100.x.x.x`) e o
> **Wi-Fi LAN**: funciona em casa e na rua (4G/5G), sem abrir portas no
> roteador. O token de 256 bits fica em `%LOCALAPPDATA%\HiveMind\remote-bridge.json`
> no PC — nenhum segredo é copiado para o celular.

## Contrato da API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/v1/health` | Status do host |
| GET | `/api/v1/projects` | Projetos abertos + lanes + aprovações |
| GET | `/api/v1/lanes?workspace=` | Estado vivo das lanes |
| GET | `/api/v1/sessions?workspace=` | Sessões salvas + tokens |
| GET | `/api/v1/session?workspace=&id=` | Mensagens de uma sessão |
| POST | `/api/v1/runs/start` | Inicia/continua execução |
| POST | `/api/v1/runs/cancel` | Para execução na lane |
| POST | `/api/v1/runs/redirect` | Cancela e redireciona a lane |
| POST | `/api/v1/approvals/resolve` | Aprova/recusa gate |
| GET | `/api/v1/git/diffs?workspace=` | Diffs do workspace |
| GET | `/api/v1/git/prs?workspace=` | PRs abertos + revisão |
| GET | `/api/v1/git/issues?workspace=` | Tasks Jira/GitHub suas |
| GET | `/api/v1/autoreview/status?workspace=` | Status do Auto-Review |
| POST | `/api/v1/autoreview/poll` | Verificação imediata |
| GET | `/api/v1/events` | **SSE** em tempo real |

Todas as rotas (exceto `/health`) exigem `Authorization: Bearer <token>`.

## Build

```powershell
# Android Studio (JDK 21) ou:
gradlew.bat assembleRelease
# APK de saída: app\build\outputs\apk\release\app-release.apk
# Copiado também para dist\HiveMindMobile.apk
```

Instalar direto no celular conectado via USB:

```powershell
adb install -r dist\HiveMindMobile.apk
```

## Stack

- Kotlin 2.1 · Jetpack Compose (Material 3) · tema Obsidian Graphite
  (`#0B0D10 / #12151B / #1A1F28` + Steel Blue `#4C8DFF` — zero roxo)
- OkHttp + okhttp-sse (SSE com reconexão automática) · kotlinx.serialization
- CameraX + ML Kit Barcode (QR Code) · DataStore (pareamento) ·
  Android 8+ (minSdk 26, targetSdk 36)
