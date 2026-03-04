# Arquitetura SLED — Daemon ↔ App Android

## Visão Geral

```
┌─────────────────┐         WebSocket (WSS :3030)         ┌─────────────────┐
│   App Android   │ ◄──────────────────────────────────── │     Daemon      │
│                 │ ────────────────────────────────────► │   (Rust/Tauri)  │
│  Chat UI        │        JSON bidirectional             │                 │
│  Tool Cards     │                                       │  GeminiProcess  │
│  Confirmações   │                                       │       │         │
└─────────────────┘                                       │       ▼         │
                                                          │  Gemini CLI     │
                                                          │  (subprocess)   │
                                                          └─────────────────┘
```

---

## Protocolo WebSocket

### App → Daemon (WsIncoming)

#### 1. Autenticação (primeira mensagem)
```json
{
  "type": "Auth",
  "payload": {
    "secret": "uuid-do-qr-code",
    "device_id": "android-device-id",
    "device_name": "Pixel 8"
  }
}
```

#### 2. Mensagem de Chat (usa streaming via GeminiProcess)
```json
{
  "type": "Message",
  "payload": {
    "text": "Meu WSL está travando, investigue isso"
  }
}
```

#### 3. Comando Estruturado (para ações específicas)
```json
{
  "type": "Command",
  "payload": {
    "name": "set_model",
    "args": { "model": "gemini-2.5-pro" }
  }
}
```

**Comandos disponíveis:**
- `set_model` — Troca modelo Gemini
- `get_model_info` — Info do modelo ativo
- `create_plan` — Cria plano de execução
- `request_plan` — Cria plano direto (sem chat)
- `approve_plan` — Aprova plano para execução
- `reject_plan` — Rejeita plano
- `set_mode` — Standard/Advanced
- `get_status` — Status do Daemon
- `get_logs` — Histórico de execuções
- `get_settings` — Configurações

---

### Daemon → App (WsOutgoing)

Os tipos abaixo são enviados pelo WebSocket em tempo real durante o streaming.

#### 1. `AuthResult` — Resultado de autenticação
```json
{
  "type": "AuthResult",
  "payload": {
    "success": true,
    "message": "Dispositivo 'Pixel 8' pareado com sucesso!"
  }
}
```

#### 2. `Thinking` — Agente processando (mostrar loading)
```json
{
  "type": "Thinking",
  "payload": {
    "stage": "Processando..."
  }
}
```

#### 3. `SessionInit` — Sessão Gemini iniciada
```json
{
  "type": "SessionInit",
  "payload": {
    "session_id": "abc123",
    "model": "gemini-2.5-flash"
  }
}
```

#### 4. `StreamChunk` — Texto da resposta (incremental)
```json
{
  "type": "StreamChunk",
  "payload": {
    "content": "Vou investigar seu WSL. ",
    "done": false
  }
}
```
Quando `done: true`, a resposta terminou.

#### 5. `ToolUse` — Ferramenta sendo chamada
```json
{
  "type": "ToolUse",
  "payload": {
    "tool_name": "shell",
    "tool_id": "call_001",
    "parameters": {
      "command": "wsl --list --verbose",
      "description": "Listando distros WSL"
    }
  }
}
```

**Ferramentas possíveis:**
| tool_name | Função | Parâmetros principais |
|-----------|--------|-----------------------|
| `shell` | Executa comando | `command`, `description`, `dir_path` |
| `read_file` | Lê arquivo | `file_path`, `start_line`, `end_line` |
| `write_file` | Cria/sobrescreve arquivo | `file_path`, `content` |
| `edit` | Edita trecho de arquivo | `file_path`, `old_string`, `new_string` |
| `grep` | Busca em arquivos | `pattern`, `dir_path` |
| `glob` | Busca arquivos por padrão | `pattern`, `dir_path` |
| `ls` | Lista diretório | `dir_path` |
| `web_search` | Busca na web | `query` |
| `web_fetch` | Lê página web | `url`, `prompt` |

#### 6. `ToolResult` — Resultado da ferramenta
```json
{
  "type": "ToolResult",
  "payload": {
    "tool_id": "call_001",
    "status": "success",
    "output": "NAME            STATE    VERSION\n* Ubuntu-24.04    Stopped  2",
    "error": null
  }
}
```

#### 7. `PromptInput` — Agente pede input do usuário
```json
{
  "type": "PromptInput",
  "payload": {
    "prompt_type": "choice",
    "label": "Reescrever .wslconfig com configuração otimizada?",
    "options": [
      "Sim, aplicar",
      "Não, cancelar",
      "Ver diff completo"
    ]
  }
}
```

**Tipos de prompt:**
- `choice` — Botões com opções (como no print)
- `text` — Input de texto livre
- `yesno` — Sim ou Não

#### 8. `StreamError` — Erro
```json
{
  "type": "StreamError",
  "payload": {
    "severity": "error",
    "message": "Rate limit atingido"
  }
}
```

#### 9. `ChatMessage` — Mensagem completa (final)
```json
{
  "type": "ChatMessage",
  "payload": {
    "id": "msg-1",
    "text": "Resposta completa acumulada...",
    "message_type": "chat",
    "metadata": null,
    "timestamp": "2026-03-04T04:15:00Z"
  }
}
```

#### 10. `Response` — Resposta a um Command
```json
{
  "type": "Response",
  "payload": {
    "command": "set_model",
    "success": true,
    "data": { "model": "gemini-2.5-pro" },
    "error": null
  }
}
```

#### 11. `ModelInfo` — Info do modelo
```json
{
  "type": "ModelInfo",
  "payload": {
    "current_model": "gemini-2.5-flash",
    "available_models": ["gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"],
    "request_count": 5,
    "last_error": null
  }
}
```

---

## Fluxo Completo — Exemplo do Print

```
1. App envia: { "type": "Message", "payload": { "text": "Meu WSL está travando" } }

2. Daemon responde (streaming em tempo real):

   → Thinking:     { stage: "Processando..." }
   → SessionInit:  { model: "gemini-2.5-flash" }
   → StreamChunk:  { content: "Vou investigar...", done: false }
   → ToolUse:      { tool_name: "shell", parameters: { command: "wsl --list --verbose" } }
   → ToolResult:   { status: "success", output: "Ubuntu-24.04 Stopped 2" }
   → ToolUse:      { tool_name: "read_file", parameters: { file_path: ".wslconfig" } }
   → ToolResult:   { output: "[wsl2]\nmemory=20GB\nswap=4GB" }
   → StreamChunk:  { content: "Seu .wslconfig reserva 20GB mas você tem apenas 16GB de RAM." }
   → PromptInput:  { prompt_type: "choice", label: "Reescrever .wslconfig?", options: ["Sim", "Não", "Ver diff"] }
   → StreamChunk:  { content: "", done: true }
   → ChatMessage:  { text: "resposta completa acumulada...", message_type: "chat" }
```

---

## Componentes Android Necessários

### 1. ChatBubble
- Renderiza `StreamChunk` — concatena chunks até `done: true`
- Suporta Markdown

### 2. ToolCard
- Renderiza `ToolUse` — mostra ícone + nome + parâmetros resumidos
- Quando chega `ToolResult` com mesmo `tool_id`, expande com output

### 3. ConfirmationCard  
- Renderiza `PromptInput`
- `choice` → botões verticais
- `text` → TextField
- `yesno` → dois botões
- **Resposta do usuário vai como nova `WsIncoming::Message`**

### 4. ErrorBanner
- Renderiza `StreamError` — toast ou banner

### 5. LoadingIndicator
- Renderiza `Thinking` — animação de "digitando..."

---

## Alterações Feitas no Daemon

### Arquivos Criados
- `src/planner/gemini_process.rs` — Motor de streaming (GeminiProcess)
- `resources/sled-prompt.md` — System prompt customizado

### Arquivos Modificados
- `src/transport/messages.rs` — +6 WsOutgoing types
- `src/transport/websocket.rs` — GeminiProcess por conexão + streaming
- `src/transport/ws_commands.rs` — `send_message` depreciado
- `src/planner/mod.rs` — Registrado módulo

### Fluxo Antigo vs Novo

| | Antigo | Novo |
|---|--------|------|
| Motor | GeminiAdapter (Rust) | GeminiProcess → Gemini CLI nativo |
| Ferramentas | 6 em Rust (read_file, shell, etc.) | 22 nativas do CLI |
| Output | JSON síncrono (espera tudo) | Stream JSON (linha por linha) |
| System Prompt | Hardcoded no PromptBuilder | `sled-prompt.md` via `GEMINI_SYSTEM_MD` |
| Velocidade | Espera resposta completa | Streaming em tempo real |
| Confirmações | Nenhuma | Nativas do CLI (ask_user) |
