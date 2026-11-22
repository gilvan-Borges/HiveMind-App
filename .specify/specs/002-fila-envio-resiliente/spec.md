# Spec 002 — Fila de envio resiliente, notificações funcionais e reconexão com jitter

> **Status:** Proposto
> **Atende:** Constituição D1–D8 (`.specify/memory/constitution.md`)
> **Escopo:** `features/chat` (Composer + painel de pendências), `MainActivity` (permissão + serviço), `core/api` (SSE), `features/relay` (polling)

## Problema

1. **Objetivo perdido no vácuo.** Com o bridge inacessível (PC dormindo, rede trocada, relay fora), o botão Enviar falha com um banner de erro e o rascunho fica preso no composer até a conexão voltar — a pessoa está no celular justamente longe do PC. Não há fila: o comando ou vai na hora ou não vai.
2. **Notificações mortas no Android 13+.** `POST_NOTIFICATIONS` é declarado no manifest e checado em `LocalNotifier.canPost()`, mas nunca pedido — aprovações pendentes e "equipe concluiu" são silenciosamente descartados pelo sistema.
3. **Conexão morre em background.** `ConnectivityService` (foreground `dataSync`) existe no manifest, mas nenhum código o inicia: o processo morre fora do foreground e as aprovações deixam de chegar até o app reabrir.
4. **Reconexão sem jitter.** O backoff do SSE (1s → 30s) é determinístico: quando o host volta, todos os aparelhos reconectam no mesmo tick.
5. **Polling fixo de 10s** na lista de IDEs, mesmo com presença instantânea via socket.

## Decisão

- **Fila durável (Room, tabela `pending_runs`, DB v2 com fallback destrutivo — cache, não fonte da verdade).** Objetivo enfileirado quando: (a) o botão Enviar é acionado com conexão != `Connected`, ou (b) o envio direto falha com `ApiException` sem status (transporte — o hub nunca recebeu). Dreno automático a cada mudança de conexão/snapshot de lanes.
- **Política de entrega (testável, `RunOutbox`):**
  - FIFO estrito entre itens `QUEUED`.
  - Lane ocupada (`running`) segura o item até o `finish` re-disparar o dreno.
  - Cooldown de 8s por lane após envio aceito — cobre a janela POST aceito → primeiro `stage` marcar `running`, impedindo dois runs empilhados por engano.
  - Falha de transporte devolve à fila e para o dreno (sem resposta do hub = seguro repetir depois).
  - Rejeição do hub (`ApiException` com status) vira `FAILED` com erro visível e botões **Repetir/Descartar** — o hub não tem idempotência; reenvio automático de comando já avaliado duplicaria run (custo de tokens).
  - `SENDING` sobrevivente de crash vira `FAILED` no restore ("verifique se a tarefa já iniciou") — estado incerto pede humano, não reenvio cego.
- **Permissão:** pedido único de `POST_NOTIFICATIONS` na primeira composição do `MainActivity` (API 33+); negativa não bloqueia o app.
- **Serviço:** `ConnectivityService` iniciado quando `pairing != null && conn == Connected` (foreground start a partir da Activity é sempre permitido), parado quando o pareamento some.
- **SSE:** loop de coroutines (1 job por geração, `close()` destrava a leitura bloqueante) com jitter ±40% no backoff.
- **Polling:** decai 10s → 60s; presença por socket continua cobrindo o meio-termo.

## Tokens visuais (D1–D4)

| Papel | Token | Uso |
|---|---|---|
| Cartão de pendência | `ObsidianCard` + borda 1dp da cor de estado | fundo neutro, estado na borda |
| Na fila | `ApprovalAmber` (dot + pill) | aviso, não alarme |
| Enviando | `InfoBlue` (dot + pill) | progresso |
| Falhou | `ErrorRed` (pill + texto do erro) | ação humana |
| Repetir | `BrandGold` (outlined) | destaque de marca |
| Descartar | `ErrorRed` (outlined) | destrutivo suave |
| Texto de apoio | `TextDim` | legenda "envia sozinho…" |

Nenhuma cor literal fora de `Theme.kt`.

## Estados (D5)

| Estado | Na fila | Enviando | Falhou |
|---|---|---|---|
| Conteúdo | objetivo + legenda de auto-envio | objetivo | objetivo + `lastError` |
| Ação | — | — | Repetir / Descartar (44dp) |
| Vazio | painel some | painel some | painel some |

O Composer continua com seus quatro estados (erro via banner, envio via spinner, vazio, dados) — a fila só existe quando tem conteúdo.

## Alvos de toque (D7)

- Repetir/Descartar: `heightIn(min = 44.dp)`, `weight(1f)` cada.
- Pills e legendas são leitura (sem alvo).

## Contratos testados

- `RunOutboxTest` (9 casos): gate de conexão, FIFO, requeue de transporte, FAILED em rejeição do hub, lane ocupada, cooldown pós-envio, retry/discard, restore de SENDING órfão, preservação de QUEUED/FAILED no restore.
- `SseBackoffTest`: dobra 1s→…, jitter ∈ [0.6, 1.4], teto 30s, piso 250ms.

## Consequências

- `AppDatabase` v1 → v2 (destrutivo: o Room é cache; a fila re-semeia do estado vivo após re-pareamento).
- O envio offline de imagens anexadas passa a ser suportado (imagens persistem com o item em JSON).
- O redirect continua exigindo conexão viva — cancelar/reiniciar um run em curso não faz sentido com o bridge fora.
