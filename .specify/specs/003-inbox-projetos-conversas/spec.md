# Spec 003 — Inbox de projetos, conversas e configurações

> **Status:** Aplicado
> **Atende:** Constituição D1–D9 (`.specify/memory/constitution.md`)
> **Referência de layout:** lista com avatar, seções colapsáveis, prévia, hora e não lidas; configurações em cartões; chat com bolhas, card embutido, divisor NOVAS e compositor.

## Navegação

1. **Home** lista os projetos da IDE conectada. Avatar abre Configurações. `+` inicia uma conversa (escolhe o projeto quando há mais de um).
2. **Projeto** lista as conversas daquele workspace, no mesmo chrome. `+` abre uma conversa nova nesse projeto. Voltar retorna aos projetos.
3. **Conversa** abre o chat e continua a sessão (`sessionId` + `laneId`) pelo `POST /api/v1/runs/start` já existente.
4. **Configurações** (toque no avatar) concentra a IDE conectada (LAN / Tailscale / relay), Fluxo, Diffs, projetos e desconectar. O pareamento em si não muda de protocolo.

Fluxo, Diffs e o gerenciador de projetos continuam acessíveis por Configurações. A barra inferior de abas sai da navegação principal.

## Dados

Contrato real do bridge (`docs/APP_MOBILE_CONTRATO.md` em `gilvan-Borges/hivemind`, branch `cursor/hive-mobile-contract-8e61`). Não há rota `/api/v1/conversations`.

- `GET /api/v1/sessions?workspace=` traz `preview`, `unread` e `kind` (`hive` ou vazio = equipe). A primeira lista no servidor zera a linha de base.
- `POST /api/v1/session/read` `{ workspace, sessionId }` zera `unread`. Host sem IDE responde 501.
- Continuar: `POST /api/v1/runs/start` com `sessionId`. Equipe usa `runMode` auto e a lane da sessão. Agent Hive usa `runMode` `resident` e `laneId` `resident`.
- Parar: `POST /api/v1/runs/cancel`. Aprovações: evento `approval_required`, `GET /api/v1/approvals`, `POST /api/v1/approvals/resolve`.
- Ao vivo: evento `activity` com `phase` `file|command|test|other`. Eventos de lane carregam `messageId`, `conversationId`, `sessionId`.
- Projetos: `permissionMode` em `GET /api/v1/projects`. `POST /api/v1/project/permission` grava `full` ou `governed`.
- `GET /api/v1/health` inclui `online`, `transport` (`lan|tailscale|relay`) e `openProject`.
- Diffs: `POST /api/v1/git/commit` e `POST /api/v1/git/pr`. Rotinas: `GET /api/v1/routines` e `POST /api/v1/routines/enabled`.
- Projetos na home agrupam pelo `stack`; vazio vira **Sem seção**. Conversas agrupam em **Agent Hive** e **Equipe**.

## Operação no celular

- Notificação de `approval_required` traz ações **Aprovar** e **Negar** (`ApprovalActionReceiver` → `POST /api/v1/approvals/resolve`). O mesmo pedido continua como card na conversa.
- Eventos `activity` viram um card ao vivo (arquivo, comando, teste) com **Parar** (`POST /api/v1/runs/cancel`).
- Diffs: revisar o patch e confirmar commit (`POST /api/v1/git/commit`) ou PR (`POST /api/v1/git/pr`).
- Pílula no topo da lista: `IDE online|offline`, `transport` e `openProject` de `GET /api/v1/health`.
- Configurações listam rotinas e pausam/retomam com `POST /api/v1/routines/enabled`.
- Voz fica de fora desta entrega. O compositor só envia texto.

## Tokens (D1–D4)

| Papel | Token |
|---|---|
| Fundo | `ObsidianBg` |
| Cartão / bolha do assistente / busca | `ObsidianCard` / `ObsidianSurface` |
| Bolha da pessoa | `SuccessGreen` com texto `OnPrimary` |
| Não lidas / divisor NOVAS | `InfoBlue` |
| Ação `+` e foco | `BrandGold` |
| Desconectar | `ErrorRed` |
| Prévia e hora | `TextDim` |

Alvos de toque ≥ 44 dp. Cada lista cobre carregando, erro com retry, vazio e dados.
