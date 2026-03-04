# Arquitetura SLED (estado atual)

Este documento descreve a arquitetura implementada hoje no repositório, com foco em comportamento real do código.

## 1. Stack e escopo

- Backend desktop: `Rust + Tauri 2`.
- UI desktop: `React 19 + Vite` embutida no app Tauri.
- Persistência: `SQLite` via `rusqlite`.
- Transporte remoto: `WebSocket` (com TLS opcionalmente habilitado, e atualmente habilitado no bootstrap).
- Motor de agente: `Gemini CLI` (fork local em `gemini-cli-fork`) executado como processo filho persistente via bridge WebSocket local.

## 2. Topologia de runtime

```text
+--------------------------------------------------------------+
|                        Processo Tauri                        |
|                                                              |
|  +------------------+                                        |
|  | React Desktop UI |<-- IPC events (daemon-event) --------+ |
|  +------------------+                                       | |
|                                                              | |
|  +------------------+     in-memory broadcast               | |
|  |    EventBus      |<-----------------------------------+  | |
|  +------------------+                                    |  | |
|          ^                                               |  | |
|          |                                               |  | |
|  +------------------+                                   |  | |
|  |    DaemonCore    |---- SQLite -----------------------+  | |
|  +------------------+                                   |  | |
|          ^                                              |  | |
|          |                                              |  | |
|  +------------------+                                   |  | |
|  |    WsServer      |--- WSS :3030 (0.0.0.0) ----------+--+-+--> Cliente móvel/externo
|  +------------------+      (mensagens WsIncoming/Outgoing)   |
|          |                                                     |
|          v                                                     |
|  +------------------+                                          |
|  | GeminiProcess    |-- localhost TCP + WS bridge ------------+|
|  +------------------+                                          ||
+--------------------------------------------------------------+||
                                                                ||
                             +----------------------------------+|
                             |   Processo filho Node (CLI)       |
                             | node .../gemini-cli-fork/...      |
                             +------------------------------------+
```

## 3. Boot sequence

Ordem de inicialização em `src-tauri/src/lib.rs`:

1. Inicializa logger (`tracing_subscriber`).
2. Instala provider criptográfico `rustls`.
3. Sobe plugins Tauri:
- `tauri-plugin-autostart` com argumento `--minimized`.
- `tauri-plugin-websocket`.
4. `setup`:
- Resolve `app_config_dir`.
- Garante certificados (`server.crt`/`server.key`) em `config/certs`.
- Inicializa SQLite (`sled.db`) e migra schema.
- Cria `EventBus` e `DaemonCore`.
- Publica `DaemonStarted` e grava execução de bootstrap.
- Inicia `WsServer` em `:3030` com TLS.
- Inicia bridge de eventos para UI (`daemon-event`).
- Configura tray.
- Se recebeu `--minimized`, esconde janela principal.
5. Registra comandos Tauri (`invoke`).

## 4. Módulos backend (Rust)

### 4.1 `daemon/`

- `orchestrator.rs` (`DaemonCore`):
- Gerencia sessões WS (ids em memória).
- Mantém canal de `Thinking` por sessão.
- Lê/escreve configuração de modelo no DB.
- Publica evento de bootstrap.

Observação importante:
- `set_gemini_model` persiste setting no DB, mas a instância de `GeminiProcess` de cada conexão ainda é criada com valor hardcoded (`gemini-2.5-flash`) em `transport/websocket.rs`.

### 4.2 `transport/`

- `messages.rs`: contrato de `WsIncoming` e `WsOutgoing`.
- `websocket.rs`: servidor WS TLS, autenticação, roteamento de mensagens e integração com `GeminiProcess`.
- `ws_commands.rs`: comandos estruturados (subset ativo).

### 4.3 `planner/`

- `gemini_process.rs`: camada de bridge entre daemon e Gemini CLI.
- `chat_session.rs`: estruturas de sessão/modo de chat (atualmente não é o fluxo principal do WS).

### 4.4 `state/`

- `database.rs`: acesso SQLite.
- `migrations.rs`: cria tabelas e settings default.
- `models.rs`: `Device`, `Execution`, `Plan`, `Step`.

Schema atual:

- `devices(id, name, paired_at)`
- `executions(id, goal, status, started_at, finished_at)`
- `settings(key, value)`

### 4.5 `auth/`

- `certs.rs`: geração/leitura de certificado self-signed.
- `pairing.rs`: payload de pareamento (`sled://host:port?secret=...`) e QR SVG.
- `devices.rs` / `tokens.rs`: utilitários existentes, mas sem papel central no fluxo WS atual.

### 4.6 `events/` + `ipc/`

- `EventBus`: `tokio::broadcast` interno.
- `EventBridge`: repassa todo evento para frontend Tauri via `daemon-event`.

### 4.7 `tray.rs`

Menu da bandeja:
- Abrir painel.
- Pausar execuções (placeholder de log).
- Modo somente leitura (placeholder de log).
- Sair completamente.

## 5. Protocolo WebSocket externo

Definido em `src-tauri/src/transport/messages.rs`.

### 5.1 Entradas (`WsIncoming`)

- `Auth`
```json
{
  "type": "Auth",
  "payload": {
    "secret": "<pairing_secret>",
    "device_id": "...",
    "device_name": "..."
  }
}
```

- `Message`
```json
{
  "type": "Message",
  "payload": { "text": "..." }
}
```

- `Command`
```json
{
  "type": "Command",
  "payload": {
    "name": "set_model",
    "args": { "model": "gemini-2.5-pro" }
  }
}
```

- `Event` (compatibilidade): envelope de evento cru.

### 5.2 Saídas (`WsOutgoing`)

- `AuthResult`
- `Response`
- `ModelInfo`
- `Thinking`
- `StreamChunk`
- `ToolUse`
- `ToolResult` (tipo existe no contrato, porém não é emitido pelo bridge atual)
- `PromptInput` (tipo existe no contrato, porém fluxo atual usa auto-approve)
- `SessionInit` (tipo existe no contrato, não usado no fluxo bridge atual)
- `StreamError`
- `ChatMessage` (tipo existe no contrato, não emitido no fluxo bridge atual)
- `Event`

## 6. Comandos WS efetivamente suportados hoje

Implementados em `transport/ws_commands.rs`:

- `set_model`
- `get_model_info`
- `get_status`
- `get_logs`
- `get_settings`
- `send_message` (explicitamente depreciado; orienta usar `WsIncoming::Message`)

Qualquer outro comando retorna erro `Comando desconhecido ou depreciado`.

## 7. Ciclo de vida de conexão WS

Em `transport/websocket.rs`, por conexão:

1. Cria `session_id` no `DaemonCore`.
2. Cria um `GeminiProcess` dedicado.
3. Tenta configurar `system prompt` em `resources/sled-prompt.md` (path relativo; fallback via path do executável).
4. Chama `gemini.start_bridge().await`.
5. Sobe task de envio que multiplexa:
- Eventos do `EventBus`.
- Respostas de comandos (`response_rx`).
- Thinking por sessão (`thinking_rx`).
- Eventos de streaming do Gemini (`gemini_rx`).
6. Sobe task de recepção:
- Trata `Auth`.
- Trata `Command`.
- Trata `Message` (dispara `gemini.send_message(text)` assíncrono).
7. Quando qualquer task termina, aborta a outra.
8. Remove sessão do `DaemonCore`.

## 8. Fluxo de autenticação de dispositivo

- O app desktop gera `pairing_secret` se ausente em `settings`.
- `get_pairing_qr` monta payload `sled://<ip>:<port>?secret=<secret>`.
- Cliente externo envia `WsIncoming::Auth` com esse secret.
- Se confere com `settings.pairing_secret`, conexão é marcada como autenticada e o dispositivo é inserido em `devices`.

Observação:
- No código atual, mensagens/comandos sem autenticação continuam sendo processados (com log de aviso) para facilitar testes.

## 9. Integração com Gemini CLI (bridge local)

`planner/gemini_process.rs`:

1. Abre `TcpListener` em `127.0.0.1:0`.
2. Spawna processo:
- `node c:\Users\izael\Downloads\Daemon\gemini-cli-fork\packages\cli\dist\index.js`
- Args: `--sandbox=false --ws-bridge=<url> -m <model>`
- Opcional: `--system-prompt-file=<path>`
3. Aguarda conexão do CLI por até 60s.
4. No handshake da bridge, recebe `BridgeConnected`.
5. Em cada mensagem de chat:
- Envia `ChatMessage { text }` para CLI.
- Repassa `StreamChunk`/`StreamEnd`/`StreamError` para WS externo.
6. Tool approvals:
- Recebe `ToolApprovalRequest`.
- Emite `WsOutgoing::ToolUse`.
- Hoje auto-responde `StdinResponse { confirmed: true }`.

Timeouts relevantes:
- 60s para CLI conectar na bridge.
- 120s para bridge ficar pronta antes do envio.
- 300s timeout de turn.

## 10. Frontend desktop (React)

Entrada:
- `src/main.jsx` monta `App`.

`src/App.jsx` hoje concentra:
- Sidebar e páginas (`chat`, `dashboard`, `security`, `settings`, `executions`).
- Cliente WebSocket via `@tauri-apps/plugin-websocket`.
- Leitura de eventos Tauri via `listen("daemon-event", ...)`.
- Renderização de stream (`assistant_stream`) e tool cards no chat.

Comandos Tauri oficialmente registrados no backend:

- `get_status`
- `get_logs`
- `get_pairing_qr`
- `get_settings`
- `update_setting`
- `get_model_info`
- `set_gemini_model`

Observação de consistência:
- Alguns helpers/frontend ainda referenciam comandos não expostos no backend atual (`request_plan`, `approve_plan`, `pause_execution`).

## 11. Segurança e rede

### 11.1 TLS

- `WsServer` usa TLS quando `with_tls` é configurado (estado atual: sim no bootstrap).
- Certificado é self-signed e persistido no diretório de configuração do app.

### 11.2 SAN do certificado

SAN atual gerado em `auth/certs.rs`:
- `localhost`
- `127.0.0.1`

Impacto:
- O servidor escuta `0.0.0.0`, mas clientes LAN conectando por IP diferente de `127.0.0.1` podem receber erro de validação de hostname TLS, a menos que ignorem validação ou usem trust custom.

### 11.3 Autorização de tools

- No bridge atual, approvals são auto-confirmados (`confirmed: true`), portanto não há gate de aprovação humano para tool calls no fluxo runtime.

## 12. Persistência e configuração

Settings em uso efetivo:

- `websocket_port`
- `timeout_seconds`
- `read_only_mode`
- `pairing_secret` (criado sob demanda)
- `gemini_model` (persistido por `set_model` / `set_gemini_model`)

Observação:
- Apesar de `websocket_port` existir no DB, o `WsServer` é inicializado com porta fixa `3030` em `lib.rs`.

## 13. End-to-end (fluxo principal atual)

```text
Cliente externo -> WSS Auth -> AuthResult
Cliente externo -> Message(text)
Daemon -> GeminiProcess.send_message
GeminiProcess -> BridgeOutgoing.ChatMessage para CLI
CLI -> BridgeIncoming.StreamChunk ... StreamEnd
GeminiProcess -> WsOutgoing.StreamChunk(done=false/true)
WsServer -> envia chunks ao cliente externo
```

## 14. Estado atual vs dívida técnica

### Implementado

- Bridge persistente Daemon <-> Gemini CLI.
- Streaming incremental de resposta para cliente WS.
- Pareamento por secret + QR.
- UI desktop com monitoramento básico.

### Incompleto / divergente

- `set_model` persiste no DB, mas não garante uso do modelo na instância de `GeminiProcess` da conexão corrente.
- Tipos `WsOutgoing` mais amplos que o fluxo realmente emitido (`SessionInit`, `ChatMessage`, `PromptInput`, `ToolResult` não aparecem no caminho principal atual).
- Comandos antigos ainda citados na UI/documentação histórica.
- Aprovação de tools está auto-approve.
- Porta WS configurável no DB ainda não aplicada ao bind do servidor.
- Certificado TLS não cobre IP LAN dinâmico por SAN.

## 15. Arquivos chave

Backend:
- `src-tauri/src/lib.rs`
- `src-tauri/src/transport/websocket.rs`
- `src-tauri/src/transport/messages.rs`
- `src-tauri/src/transport/ws_commands.rs`
- `src-tauri/src/planner/gemini_process.rs`
- `src-tauri/src/daemon/orchestrator.rs`
- `src-tauri/src/state/database.rs`
- `src-tauri/src/auth/certs.rs`

Frontend:
- `src/main.jsx`
- `src/App.jsx`
- `src/utils/ipc.js`
- `src/utils/events.js`

Fork Gemini CLI:
- `gemini-cli-fork/packages/cli/src/gemini.tsx`
- `gemini-cli-fork/packages/cli/src/ws-bridge.ts`
- `gemini-cli-fork/packages/cli/dist/index.js`
