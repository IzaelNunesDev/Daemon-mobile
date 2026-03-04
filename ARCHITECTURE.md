# Arquitetura SLED (estado atual real)

Atualizado em: 2026-03-04

Este documento descreve o comportamento implementado hoje no daemon (`Rust/Tauri`) + bridge do Gemini CLI fork, com foco no fluxo interativo mobile.

## 1. Stack e escopo

- Backend desktop: `Rust + Tauri 2`
- UI desktop: `React + Vite`
- Persistência: `SQLite (rusqlite)`
- Transporte remoto app/daemon: `WebSocket` com TLS (self-signed)
- Engine: `gemini-cli-fork` (processo Node persistente) conectado por `ws-bridge` local

## 2. Topologia de runtime

```text
Mobile App (Android)
  └─ WSS -> WsServer (Daemon)
          ├─ Auth / Message / Command / StdinResponse
          ├─ Sessão + EventBus + SQLite
          └─ GeminiProcess (por conexão)
                └─ ws://127.0.0.1:<porta-aleatória> (bridge local)
                     └─ node gemini-cli-fork/packages/cli/dist/index.js
```

## 3. Boot e rede

1. O daemon tenta bind em `0.0.0.0:3030`.
1. Se a porta estiver ocupada, faz fallback incremental (`3031+`).
1. A porta efetiva é persistida sempre em `settings.websocket_port`.
1. Cada conexão WS cria uma sessão e um `GeminiProcess` dedicado.
1. O `GeminiProcess` sobe um bridge local em porta efêmera (`127.0.0.1:0`) e spawna o CLI fork com:
   - `--ws-bridge=ws://127.0.0.1:<porta>`
   - `--sandbox=false`
   - `-m <modelo>`
   - `--system-prompt-file=<path>` (quando disponível)

## 4. Contrato WS externo (App <-> Daemon)

Arquivo fonte: `src-tauri/src/transport/messages.rs`.

### 4.1 App -> Daemon (`WsIncoming`)

- `Auth { secret, device_id, device_name }`
- `Message { text }`
- `Command { name, args }`
- `StdinResponse { correlationId|correlation_id, confirmed, answers? }`
- Compat legados:
  - `ToolApprovalResponse { correlationId, approved, answers? }`
  - `AskUserResponse { correlationId, answer }`

### 4.2 Daemon -> App (`WsOutgoing`)

- `AuthResult`
- `Response`
- `Thinking`
- `StreamChunk { content, done }`
- `ChatMessage` (mensagem final acumulada)
- `ToolUse`
- `ToolResult`
- `ToolApprovalRequest`
  - `tool_id`
  - `tool_name`
  - `command?`
  - `risk_level?`
  - `correlation_id`
  - `confirmation_type?`
  - `details?`
  - `tool_call?`
- `AskUser { title, questions[], correlation_id }`
- `BrowserAuth { url, code?, instruction?, correlation_id }`
- `PromptInput` (suporte geral)
- `StreamError`
- `Event`

## 5. Bridge daemon <-> CLI (interno)

Arquivo fonte: `src-tauri/src/planner/gemini_process.rs` e `gemini-cli-fork/packages/cli/src/ws-bridge.ts`.

### 5.1 Daemon -> CLI (`BridgeOutgoing`)

- `ChatMessage { text }`
- `Abort`
- `StdinResponse { correlationId, confirmed, answers? }`

### 5.2 CLI -> Daemon (`BridgeIncoming`)

- `BridgeConnected`
- `StreamChunk { text }`
- `StreamEnd`
- `ToolResult`
- `StreamStopped`
- `StreamError`
- `ToolApprovalRequest { correlationId, toolCall }`

### 5.3 Regra de prontidão

- Handshake TCP/WS não é suficiente.
- O daemon só marca bridge como pronta após `BridgeConnected` (ou primeiro evento útil).
- Isso evita corrida onde a primeira `ChatMessage` era enviada cedo demais.

## 6. Fluxo de turno

1. App envia `Message`.
1. Daemon envia `Thinking`.
1. Daemon encaminha para CLI via `BridgeOutgoing::ChatMessage`.
1. CLI retorna `StreamChunk*`.
1. Daemon retransmite chunks para o app.
1. Em `StreamEnd`, daemon:
   - emite `ChatMessage` final
   - emite `StreamChunk { done: true }`
   - encerra o estado de execução do turno

Timeouts:
- 60s para CLI conectar no bridge.
- 120s para bridge ficar pronta.
- 300s por turno.

## 7. Arquitetura interativa de aprovações

Origem: o CLI publica chamadas em `awaiting_approval` com `correlationId` e `confirmationDetails`.

Mapeamento implementado no daemon:

- `confirmationDetails.type == "ask_user"`:
  - envia `WsOutgoing::AskUser` (card de formulário no mobile)
- `confirmationDetails.type == "info"` + URL HTTP(S):
  - envia `WsOutgoing::BrowserAuth` (card de autenticação no navegador)
- demais tipos (`exec`, `edit`, `mcp`, `exit_plan_mode`, fallback):
  - envia `WsOutgoing::ToolApprovalRequest` com campos canônicos

Além disso, o daemon sempre emite `ToolUse` para histórico visual da execução.

## 8. Auto-approve

Estado atual:

- Auto-approve não é mais implícito de build debug.
- Só ativa com variável de ambiente explícita:
  - `SLED_WS_AUTO_APPROVE=1|true|yes|on`

Com auto-approve ativo:
- O daemon responde direto ao CLI com `StdinResponse(confirmed=true)`.
- Não envia card interativo para evitar UI presa em `isWaitingInput=true`.

## 9. Comandos WS relevantes

Em `src-tauri/src/transport/ws_commands.rs`:

- `set_model`
- `get_model_info`
- `get_status`
- `get_logs`
- `get_settings`
- `open_browser { url }` (novo para o card BrowserAuth abrir no host/PC)

## 10. Diagnóstico do “Aguardando resposta...” no mobile

Baseado no `ChatViewModel` fornecido:

- `isWaitingInput` é setado para `true` ao receber:
  - `onToolApprovalRequest`
  - `onAskUser`
  - `onBrowserAuth`
  - `onPromptInput`
- Ele só volta para `false` quando o usuário envia resposta por um desses cards.

Impacto:
- Se o daemon auto-aprova sem interação humana, o app pode ficar travado em `Aguardando resposta...` sem um card pendente visível.

## 11. Próximo passo no app (obrigatório para UX robusta)

No `ChatViewModel` Android:

1. Limpar `isWaitingInput` também em eventos de fim de turno:
   - `onStreamChunk(done=true)`
   - `onChatMessageReceived`
   - `onStreamError`
1. Guardar e consumir múltiplos prompts pendentes por `correlationId` (fila/mapa), em vez de um único boolean.
1. Incluir `correlationId` no parser de `PromptInput` caso esse tipo seja usado.
1. Tratar `ToolApprovalRequest` pelo `correlation_id` mesmo quando há múltiplas aprovações no mesmo turno.

## 12. Compatibilidade esperada

Para esse fluxo funcionar de ponta a ponta:

- Daemon e app devem concordar nos campos:
  - `correlationId/correlation_id`
  - `confirmed/approved`
  - `answers`
- Mobile deve responder interações sempre via `StdinResponse` canônico.
- O fork do CLI deve estar compilado com `ws-bridge.ts` atual (encaminhando `awaiting_approval` e `TOOL_CONFIRMATION_RESPONSE`).

## 13. Arquivos-chave

- Daemon WS: `src-tauri/src/transport/websocket.rs`
- Contrato WS: `src-tauri/src/transport/messages.rs`
- Bridge engine: `src-tauri/src/planner/gemini_process.rs`
- Comandos WS: `src-tauri/src/transport/ws_commands.rs`
- Fork bridge: `gemini-cli-fork/packages/cli/src/ws-bridge.ts`
- Mapa do fork: `docs/antigravity_insights/f733f5ea_gemini-cli-architecture-map.md`
