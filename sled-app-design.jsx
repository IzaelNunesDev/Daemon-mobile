import { useState, useEffect, useRef } from "react";

const T = {
  bg0: "#05060a", bg1: "#0c0d14", bg2: "#111320",
  indigo: "#6366f1", violet: "#8b5cf6",
  neon: "#00ff9d", cyan: "#00d4ff",
  amber: "#f59e0b", red: "#ff4455", rose: "#fb7185",
  t1: "#f0f2ff", t2: "#9ba3c2", t3: "#4a5070", t4: "#272a3d",
  b1: "rgba(255,255,255,0.07)", b2: "rgba(255,255,255,0.04)",
};

const GLOBAL_CSS = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600;700&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  @keyframes pdot  { 0%,100%{opacity:.2;transform:scale(.7)} 50%{opacity:1;transform:scale(1.3)} }
  @keyframes fin   { from{opacity:0;transform:translateY(8px)} to{opacity:1;transform:translateY(0)} }
  @keyframes scan  { 0%{transform:translateY(-100%)} 100%{transform:translateY(400px)} }
  @keyframes glow  { 0%,100%{box-shadow:0 0 8px #6366f130} 50%{box-shadow:0 0 24px #6366f170,0 0 40px #6366f125} }
  @keyframes npulse{ 0%,100%{box-shadow:0 0 5px #00ff9d30} 50%{box-shadow:0 0 14px #00ff9d80} }
  @keyframes sup   { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
  ::-webkit-scrollbar{width:3px} ::-webkit-scrollbar-thumb{background:#1a1f2e;border-radius:4px}
`;

// FLOW items — plain data, never rendered directly as React children
const FLOW = [
  { id:0,  type:"user",      text:"instalei o wsl no meu windows mas ele da muito crash, resolva isso" },
  { id:1,  type:"assistant", text:"Investigando. Vou verificar o estado atual do WSL e a RAM disponível.", tool:"shell", toolCmd:"wsl --list --verbose" },
  { id:2,  type:"stream",    lines:["  NAME            STATE     VERSION","* Ubuntu-24.04    Stopped   2"] },
  { id:3,  type:"assistant", text:"Seu .wslconfig reserva 20 GB mas você tem apenas 16 GB de RAM. Esse é o crash. Vou corrigir.", tool:"read_file", toolCmd:".wslconfig" },
  { id:4,  type:"prompt",    promptType:"confirm",  label:"Reescrever .wslconfig com configuração otimizada?", hint:"memory=6GB · swap=12GB · autoMemoryReclaim=gradual", options:["S — Sim, aplicar","N — Não, cancelar","V — Ver diff completo"], dflt:"S" },
  { id:5,  type:"assistant", text:"Configuração aplicada. Reiniciando WSL para ativar as mudanças.", tool:"shell", toolCmd:"wsl --shutdown && wsl -e free -h" },
  { id:6,  type:"stream",    lines:["             total   used   free","Mem:         5.8Gi  604Mi  4.9Gi","Swap:         12Gi    0B   12Gi"] },
  { id:7,  type:"assistant", text:"WSL estabilizado. Agora vou atualizar os pacotes do Ubuntu.", tool:"shell", toolCmd:"sudo apt update && sudo apt upgrade -y" },
  { id:8,  type:"prompt",    promptType:"password", label:"[sudo] password for izael:", hint:"Necessário para apt upgrade no Ubuntu" },
  { id:9,  type:"stream",    lines:["Hit:1 noble InRelease [126 kB]","Hit:2 noble-security InRelease [126 kB]","Fetched 252 kB in 3s","42 packages can be upgraded."] },
  { id:10, type:"prompt",    promptType:"confirm",  label:"Continuar com o upgrade?", hint:"42 pacotes serão atualizados · 238 MB", options:["Y — Yes, continuar","N — No, cancelar","D — Ver detalhes"], dflt:"Y" },
  { id:11, type:"stream",    lines:["Unpacking libc6:amd64 (2.39) ...","Setting up libssl3t64 ...","Processing triggers for man-db ...","done."] },
  { id:12, type:"assistant", text:"WSL totalmente otimizado. 42 pacotes atualizados. Sistema estável.", success:true },
];

const mono = (sz, color, extra={}) => ({ fontFamily:"IBM Plex Mono, monospace", fontSize:sz, color, ...extra });

// ── DECORATIVE ─────────────────────────────────────────────────────────────
function Scanline() {
  return (
    <div style={{position:"absolute",inset:0,overflow:"hidden",pointerEvents:"none",zIndex:1,borderRadius:"inherit"}}>
      <div style={{position:"absolute",left:0,right:0,height:"28%",background:"linear-gradient(to bottom,transparent,rgba(99,102,241,.012),transparent)",animation:"scan 9s linear infinite"}}/>
      <div style={{position:"absolute",inset:0,background:"repeating-linear-gradient(0deg,transparent,transparent 2px,rgba(0,0,0,.07) 2px,rgba(0,0,0,.07) 4px)",borderRadius:"inherit"}}/>
    </div>
  );
}

// ── PHONE SHELL ────────────────────────────────────────────────────────────
function PhoneShell({ children }) {
  return (
    <div style={{width:320,height:640,flexShrink:0,background:"#080a10",borderRadius:40,border:"1.5px solid #1e2240",boxShadow:"0 50px 100px rgba(0,0,0,.8),0 0 60px rgba(99,102,241,.06),inset 0 1px 0 rgba(255,255,255,.05)",overflow:"hidden",position:"relative",display:"flex",flexDirection:"column"}}>
      <Scanline/>
      <div style={{position:"absolute",top:0,left:"50%",transform:"translateX(-50%)",width:90,height:22,background:"#080a10",borderRadius:"0 0 14px 14px",zIndex:20,border:"1px solid #1e2240",borderTop:"none",display:"flex",alignItems:"center",justifyContent:"center",gap:6}}>
        <div style={{width:5,height:5,borderRadius:"50%",background:"#1e2240"}}/>
        <div style={{width:28,height:5,borderRadius:3,background:"#1e2240"}}/>
      </div>
      {children}
    </div>
  );
}

function StatusBar() {
  const now = new Date();
  const t = `${String(now.getHours()).padStart(2,"0")}:${String(now.getMinutes()).padStart(2,"0")}`;
  return (
    <div style={{height:32,display:"flex",alignItems:"center",justifyContent:"space-between",padding:"22px 18px 0",flexShrink:0,position:"relative",zIndex:10}}>
      <span style={mono(10,T.t3,{fontWeight:600})}>{t}</span>
      <div style={{display:"flex",alignItems:"center",gap:8}}>
        <div style={{display:"flex",alignItems:"center",gap:3}}>
          <div style={{width:5,height:5,borderRadius:"50%",background:T.neon,animation:"npulse 2s ease-in-out infinite"}}/>
          <span style={mono(8,T.neon)}>38ms</span>
        </div>
        <div style={{display:"flex",gap:1.5,alignItems:"flex-end"}}>
          {[3,5,7,9].map((h,i)=><div key={i} style={{width:3,height:h,background:i<3?T.t2:T.t4,borderRadius:1}}/>)}
        </div>
        <div style={{display:"flex",alignItems:"center",gap:2}}>
          <div style={{width:16,height:8,border:`1px solid ${T.t3}`,borderRadius:2,display:"flex",alignItems:"center",padding:1}}>
            <div style={{width:"66%",height:"100%",background:T.neon,borderRadius:1}}/>
          </div>
          <div style={{width:2,height:4,background:T.t3,borderRadius:1}}/>
        </div>
      </div>
    </div>
  );
}

function AppHeader({ mode, onToggle }) {
  return (
    <div style={{padding:"10px 16px",borderBottom:`1px solid ${T.b1}`,display:"flex",alignItems:"center",gap:10,flexShrink:0,background:`linear-gradient(to bottom,${T.bg2},${T.bg1})`,position:"relative",zIndex:10}}>
      <div style={{width:36,height:36,borderRadius:12,background:`linear-gradient(135deg,${T.indigo}cc,${T.violet}cc)`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:16,border:`1px solid ${T.indigo}40`,boxShadow:`0 0 16px ${T.indigo}30`,flexShrink:0}}>⬡</div>
      <div style={{flex:1}}>
        <div style={mono(13,T.t1,{fontWeight:700,letterSpacing:1})}>SLED</div>
        <div style={{display:"flex",alignItems:"center",gap:5,marginTop:2}}>
          <div style={{width:5,height:5,borderRadius:"50%",background:T.neon,animation:"npulse 2s ease-in-out infinite"}}/>
          <span style={mono(9,T.neon)}>PC-IZAEL conectado</span>
        </div>
      </div>
      <div onClick={onToggle} style={{display:"flex",borderRadius:20,background:T.bg0,border:`1px solid ${T.b1}`,overflow:"hidden",cursor:"pointer",flexShrink:0}}>
        {["Padrão","Avançado"].map(m=>(
          <div key={m} style={{padding:"4px 9px",...mono(8,mode===m?T.t1:T.t3),background:mode===m?(m==="Avançado"?`linear-gradient(135deg,${T.indigo},${T.violet})`:T.bg2):"transparent",fontWeight:mode===m?700:400,transition:"all .25s"}}>
            {m}
          </div>
        ))}
      </div>
    </div>
  );
}

const QA_ITEMS = [
  {icon:"◈",label:"Screenshot",color:T.cyan},
  {icon:"◉",label:"Processos", color:T.violet},
  {icon:"◆",label:"Sysinfo",   color:T.indigo},
  {icon:"⬡",label:"Clipboard", color:T.amber},
  {icon:"▣",label:"Terminal",  color:T.neon},
  {icon:"◀",label:"Arquivos",  color:T.rose},
];

function QuickActionsBar() {
  const [active,setActive] = useState(null);
  return (
    <div style={{padding:"8px 12px 4px",display:"flex",gap:6,overflowX:"auto",flexShrink:0}}>
      {QA_ITEMS.map((a,i)=>(
        <button key={i}
          onPointerDown={()=>setActive(i)}
          onPointerUp={()=>setTimeout(()=>setActive(null),300)}
          style={{display:"flex",flexDirection:"column",alignItems:"center",gap:3,padding:"7px 10px",background:active===i?`${a.color}18`:`${a.color}08`,border:`1px solid ${active===i?a.color+"50":a.color+"18"}`,borderRadius:11,cursor:"pointer",transition:"all .15s",transform:active===i?"scale(.94)":"scale(1)",flexShrink:0}}>
          <span style={{fontSize:14,color:a.color}}>{a.icon}</span>
          <span style={mono(7.5,active===i?a.color:T.t3,{whiteSpace:"nowrap"})}>{a.label}</span>
        </button>
      ))}
    </div>
  );
}

// ── BUBBLES ────────────────────────────────────────────────────────────────
function UserBubble({ text }) {
  return (
    <div style={{display:"flex",justifyContent:"flex-end",animation:"fin .25s ease-out both"}}>
      <div style={{background:`linear-gradient(135deg,${T.indigo},${T.violet})`,borderRadius:"14px 4px 14px 14px",padding:"9px 13px",...mono(11.5,"#fff"),maxWidth:"78%",boxShadow:`0 4px 16px ${T.indigo}40`,lineHeight:1.5}}>
        {text}
      </div>
    </div>
  );
}

function AssistantBubble({ text, tool, toolCmd, success }) {
  return (
    <div style={{animation:"fin .3s ease-out both"}}>
      {tool && (
        <div style={{display:"flex",alignItems:"center",gap:6,background:`${T.indigo}0a`,border:`1px solid ${T.indigo}20`,borderRadius:6,padding:"4px 8px",marginBottom:6}}>
          <span style={{fontSize:8,color:T.indigo}}>▸</span>
          <span style={mono(9,T.indigo,{fontWeight:600})}>{tool}</span>
          <span style={mono(8,T.t3,{overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",maxWidth:160})}>{toolCmd}</span>
        </div>
      )}
      <div style={{background:success?`linear-gradient(135deg,${T.neon}08,${T.bg2})`:`linear-gradient(135deg,${T.bg2},${T.bg1})`,border:`1px solid ${success?T.neon+"30":T.b1}`,borderRadius:"4px 14px 14px 14px",padding:"9px 12px",...mono(11.5,success?"#b8ffd8":T.t2),lineHeight:1.55}}>
        {success && <span style={{color:T.neon,marginRight:5}}>✓</span>}
        {text}
      </div>
    </div>
  );
}

function StreamBubble({ lines }) {
  return (
    <div style={{background:T.bg0,border:`1px solid ${T.b2}`,borderRadius:8,padding:"8px 10px",animation:"fin .3s ease-out both"}}>
      {lines.map((l,i)=><div key={i} style={mono(9.5,"#3ddc84",{lineHeight:1.7})}>{l}</div>)}
    </div>
  );
}

function ThinkingDots() {
  return (
    <div style={{display:"flex",alignItems:"center",gap:6,padding:"4px 0"}}>
      <div style={{display:"flex",gap:4}}>
        {[0,1,2].map(i=><div key={i} style={{width:5,height:5,borderRadius:"50%",background:T.indigo,animation:`pdot 1.3s ease-in-out ${i*.18}s infinite`}}/>)}
      </div>
      <span style={mono(9,T.t3)}>Gemini processando...</span>
    </div>
  );
}

// ── INTERACTIVE PROMPTS ────────────────────────────────────────────────────
function PasswordPrompt({ label, hint, onSubmit }) {
  const [val,setVal] = useState("");
  const [show,setShow] = useState(false);
  const [sent,setSent] = useState(false);
  const send = () => { if (!val || sent) return; setSent(true); setTimeout(()=>onSubmit("••••••••"), 180); };
  return (
    <div style={{background:"linear-gradient(135deg,#120e04,#0c0d14)",border:`1px solid ${T.amber}30`,borderRadius:14,padding:14,animation:"fin .3s ease-out both"}}>
      <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:10}}>
        <div style={{width:30,height:30,borderRadius:10,background:`${T.amber}15`,border:`1px solid ${T.amber}30`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:14}}>🔐</div>
        <div>
          <div style={mono(9,T.amber,{fontWeight:700,letterSpacing:2,textTransform:"uppercase"})}>Senha Necessária</div>
          <div style={mono(9,T.t3,{marginTop:2})}>{hint}</div>
        </div>
      </div>
      <div style={mono(10,T.t2,{marginBottom:8})}>{label}</div>
      <div style={{display:"flex",gap:6}}>
        <div style={{flex:1,display:"flex",alignItems:"center",background:T.bg0,border:`1px solid ${T.amber}25`,borderRadius:9,padding:"7px 10px"}}>
          <input
            type={show?"text":"password"}
            value={val}
            onChange={e=>setVal(e.target.value)}
            onKeyDown={e=>e.key==="Enter"&&send()}
            placeholder="••••••••"
            style={{flex:1,background:"none",border:"none",outline:"none",...mono(13,T.t1),width:0}}
          />
          <button onClick={()=>setShow(!show)} style={{background:"none",border:"none",cursor:"pointer",...mono(11,T.t3),padding:"0 2px"}}>
            {show?"○":"●"}
          </button>
        </div>
        <button onClick={send} disabled={!val||sent} style={{background:val&&!sent?`linear-gradient(135deg,${T.amber},#ef4444)`:T.bg2,border:"none",borderRadius:9,padding:"0 14px",...mono(14,val&&!sent?"#fff":T.t3,{fontWeight:700}),cursor:val&&!sent?"pointer":"default",transition:"all .2s",boxShadow:val&&!sent?`0 4px 14px ${T.amber}40`:"none"}}>
          {sent?"✓":"→"}
        </button>
      </div>
    </div>
  );
}

function ConfirmPrompt({ label, hint, options, dflt, onSubmit }) {
  const [sel,setSel] = useState(null);
  const pick = (letter) => { if (sel) return; setSel(letter); setTimeout(()=>onSubmit(letter), 220); };
  const colors = {"S":T.neon,"Y":T.neon,"N":T.red,"V":T.cyan,"D":T.cyan};
  return (
    <div style={{background:"linear-gradient(135deg,#0d0e1a,#0c0d14)",border:`1px solid ${T.indigo}30`,borderRadius:14,padding:14,animation:"fin .3s ease-out both"}}>
      <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:10}}>
        <div style={{width:30,height:30,borderRadius:10,background:`${T.indigo}15`,border:`1px solid ${T.indigo}30`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:14}}>⚡</div>
        <div>
          <div style={mono(9,T.indigo,{fontWeight:700,letterSpacing:2,textTransform:"uppercase"})}>Confirmação</div>
          <div style={mono(9,T.t3,{marginTop:2})}>{hint}</div>
        </div>
      </div>
      <div style={mono(11,T.t1,{fontWeight:600,marginBottom:10})}>{label}</div>
      <div style={{display:"flex",flexDirection:"column",gap:5}}>
        {options.map((opt,i)=>{
          const letter = opt[0];
          const isDefault = letter === dflt;
          const isSel = sel === letter;
          const c = colors[letter] || T.t2;
          return (
            <button key={i} onClick={()=>pick(letter)} style={{background:isSel?`${c}20`:isDefault?`${c}0d`:"rgba(255,255,255,.02)",border:`1px solid ${isDefault||isSel?c+"40":T.b2}`,borderRadius:9,padding:"8px 12px",cursor:"pointer",textAlign:"left",...mono(10.5,isDefault||isSel?c:T.t3),display:"flex",alignItems:"center",gap:9,transition:"all .15s",transform:isSel?"scale(.98)":"scale(1)"}}>
              <span style={{width:22,height:22,borderRadius:6,background:isDefault?`${c}20`:"rgba(255,255,255,.04)",border:`1px solid ${isDefault?c+"50":T.b2}`,display:"flex",alignItems:"center",justifyContent:"center",...mono(10,isDefault?c:T.t3,{fontWeight:700}),flexShrink:0}}>
                {letter}
              </span>
              <span>{opt.slice(4)}</span>
              {isDefault&&!isSel&&<span style={{marginLeft:"auto",...mono(8,T.t4)}}>padrão</span>}
              {isSel&&<span style={{marginLeft:"auto"}}>✓</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function InputBar({ disabled }) {
  return (
    <div style={{padding:"10px 12px",borderTop:`1px solid ${T.b1}`,background:T.bg1,flexShrink:0,position:"relative",zIndex:10}}>
      <div style={{display:"flex",alignItems:"center",gap:8,background:T.bg0,border:`1px solid ${T.b1}`,borderRadius:14,padding:"8px 12px",opacity:disabled?.5:1}}>
        <span style={mono(10,T.t4,{flex:1})}>{disabled?"Aguardando resposta...":"Mensagem ao SLED..."}</span>
        <div style={{display:"flex",gap:8,alignItems:"center"}}>
          <span style={{fontSize:12,color:T.t4}}>⊕</span>
          <div style={{width:28,height:28,borderRadius:9,background:disabled?T.bg2:`linear-gradient(135deg,${T.indigo},${T.violet})`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:12,color:disabled?T.t3:"#fff",boxShadow:disabled?"none":`0 4px 12px ${T.indigo}50`}}>▶</div>
        </div>
      </div>
    </div>
  );
}

// ── PHONE DEMO — clean state machine ──────────────────────────────────────
// Message shapes (all plain primitives — never FLOW objects):
// {kind:"user"|"assistant"|"stream"|"pw_prompt"|"cf_prompt"|"pw_done"|"cf_done"|"user_reply", ...}

function PhoneDemo() {
  const [mode,setMode] = useState("Padrão");
  const [msgs,setMsgs] = useState([]);
  const [started,setStarted] = useState(false);
  const [thinking,setThinking] = useState(false);
  const [waitFlowId,setWaitFlowId] = useState(null);
  const [finished,setFinished] = useState(false);
  const scrollRef = useRef();
  const timer = useRef();

  useEffect(()=>{
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  },[msgs,thinking]);

  const push = (msg) => setMsgs(prev => [...prev, {...msg, _k: Math.random()}]);

  const runFrom = (idx) => {
    if (idx >= FLOW.length) { setThinking(false); setFinished(true); return; }
    const f = FLOW[idx];

    if (f.type === "prompt") {
      setThinking(false);
      if (f.promptType === "password") {
        push({ kind:"pw_prompt", flowId:f.id, label:f.label, hint:f.hint });
      } else {
        push({ kind:"cf_prompt", flowId:f.id, label:f.label, hint:f.hint, options:f.options, dflt:f.dflt });
      }
      setWaitFlowId(f.id);
      return;
    }

    const delay = idx === 0 ? 0 : f.type === "stream" ? 900 : 600;
    timer.current = setTimeout(()=>{
      setThinking(false);
      if (f.type === "user")      push({ kind:"user",      text:f.text });
      if (f.type === "assistant") push({ kind:"assistant", text:f.text, tool:f.tool, toolCmd:f.toolCmd, success:!!f.success });
      if (f.type === "stream")    push({ kind:"stream",    lines:f.lines });
      setThinking(true);
      timer.current = setTimeout(()=>runFrom(idx+1), 240);
    }, delay);
  };

  const answer = (flowId, display) => {
    setMsgs(prev => prev.map(m => {
      if (m.kind === "pw_prompt" && m.flowId === flowId) return {...m, kind:"pw_done"};
      if (m.kind === "cf_prompt" && m.flowId === flowId) return {...m, kind:"cf_done"};
      return m;
    }));
    push({ kind:"user_reply", text:display });
    setWaitFlowId(null);
    setThinking(true);
    const nextIdx = FLOW.findIndex(f => f.id === flowId) + 1;
    timer.current = setTimeout(()=>runFrom(nextIdx), 420);
  };

  const start = () => {
    clearTimeout(timer.current);
    setMsgs([]); setStarted(true); setThinking(false);
    setWaitFlowId(null); setFinished(false);
    timer.current = setTimeout(()=>runFrom(0), 200);
  };

  const reset = () => {
    clearTimeout(timer.current);
    setMsgs([]); setStarted(false); setThinking(false);
    setWaitFlowId(null); setFinished(false);
  };

  return (
    <PhoneShell>
      <StatusBar/>
      <AppHeader mode={mode} onToggle={()=>setMode(m=>m==="Padrão"?"Avançado":"Padrão")}/>
      <QuickActionsBar/>

      <div ref={scrollRef} style={{flex:1,overflowY:"auto",padding:"10px 12px 6px",display:"flex",flexDirection:"column",gap:8}}>
        {msgs.length===0&&(
          <div style={{flex:1,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:14,opacity:.35}}>
            <div style={{fontSize:38,color:T.indigo}}>⬡</div>
            <div style={mono(10,T.t3,{textAlign:"center",lineHeight:1.8})}>{"Daemon ativo\nToque para simular"}</div>
          </div>
        )}

        {msgs.map(m => {
          const k = String(m._k);
          if (m.kind==="user")      return <UserBubble      key={k} text={m.text}/>;
          if (m.kind==="user_reply")return <UserBubble      key={k} text={m.text}/>;
          if (m.kind==="assistant") return <AssistantBubble key={k} text={m.text} tool={m.tool} toolCmd={m.toolCmd} success={m.success}/>;
          if (m.kind==="stream")    return <StreamBubble    key={k} lines={m.lines}/>;
          if (m.kind==="pw_prompt") return <PasswordPrompt  key={k} label={m.label} hint={m.hint} onSubmit={()=>answer(m.flowId,"••••••••")}/>;
          if (m.kind==="cf_prompt") return <ConfirmPrompt   key={k} label={m.label} hint={m.hint} options={m.options} dflt={m.dflt} onSubmit={v=>answer(m.flowId,v)}/>;
          if (m.kind==="pw_done")   return <div key={k} style={mono(8,T.t4,{padding:"2px 4px"})}>{"🔐 Senha enviada"}</div>;
          if (m.kind==="cf_done")   return <div key={k} style={mono(8,T.t4,{padding:"2px 4px"})}>{"⚡ Confirmado"}</div>;
          return null;
        })}

        {started&&thinking&&!finished&&<ThinkingDots/>}

        {finished&&(
          <div style={{display:"flex",justifyContent:"center",padding:"10px 0",animation:"fin .4s ease-out both"}}>
            <div style={{display:"flex",alignItems:"center",gap:8,background:`${T.neon}10`,border:`1px solid ${T.neon}30`,borderRadius:99,padding:"6px 14px",...mono(9,T.neon)}}>
              <span style={{animation:"npulse 2s ease-in-out infinite"}}>●</span>
              {"Sessão concluída · WSL estável"}
            </div>
          </div>
        )}
      </div>

      <InputBar disabled={started&&!finished}/>

      {!started&&(
        <div style={{position:"absolute",inset:0,display:"flex",alignItems:"center",justifyContent:"center",background:"rgba(5,6,10,.78)",backdropFilter:"blur(4px)",zIndex:50,borderRadius:"inherit"}}>
          <button onClick={start} style={{background:`linear-gradient(135deg,${T.indigo},${T.violet})`,border:"none",borderRadius:16,padding:"14px 30px",...mono(12,"#fff",{fontWeight:700,letterSpacing:1}),cursor:"pointer",boxShadow:`0 8px 32px ${T.indigo}60`,animation:"glow 2s ease-in-out infinite"}}>
            {"▶ Simular Interação"}
          </button>
        </div>
      )}

      {finished&&(
        <div style={{position:"absolute",bottom:78,left:12,right:12,background:T.bg0,border:`1px solid ${T.neon}40`,borderRadius:12,padding:"10px 14px",display:"flex",justifyContent:"space-between",alignItems:"center",zIndex:40,animation:"sup .4s ease-out both",boxShadow:`0 0 24px ${T.neon}18`}}>
          <span style={mono(9,T.neon)}>{"✓ Concluído"}</span>
          <button onClick={reset} style={{background:`${T.neon}15`,border:`1px solid ${T.neon}40`,borderRadius:8,padding:"5px 12px",...mono(9,T.neon),cursor:"pointer"}}>{"↺ Reiniciar"}</button>
        </div>
      )}
    </PhoneShell>
  );
}

// ── MINI SCREENS ───────────────────────────────────────────────────────────
function MiniPhone({ label, children }) {
  return (
    <div style={{display:"flex",flexDirection:"column",alignItems:"center",gap:10}}>
      <div style={{width:170,height:310,background:"#080a10",borderRadius:26,border:"1.5px solid #1e2240",overflow:"hidden",boxShadow:"0 20px 40px rgba(0,0,0,.6)",position:"relative",flexShrink:0}}>
        <Scanline/>
        <div style={{position:"relative",zIndex:2,paddingTop:18}}>{children}</div>
      </div>
      <div style={mono(8.5,T.t3,{textAlign:"center",letterSpacing:1})}>{label}</div>
    </div>
  );
}

function MiniHeader({ title, icon, color, badge }) {
  return (
    <div style={{display:"flex",alignItems:"center",gap:6,padding:"6px 8px 4px"}}>
      <div style={{width:20,height:20,borderRadius:7,background:`${color||T.indigo}18`,border:`1px solid ${color||T.indigo}30`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:10,color:color||T.indigo}}>{icon||"⬡"}</div>
      <span style={mono(9,T.t1,{fontWeight:700,flex:1})}>{title}</span>
      {badge&&<div style={{background:`${T.amber}20`,border:`1px solid ${T.amber}30`,borderRadius:5,padding:"1px 5px",...mono(7,T.amber)}}>{badge}</div>}
    </div>
  );
}

function ScreenProcesses() {
  const rows=[
    {name:"Discord",   mem:"248 MB",cpu:"0.2%", high:false},
    {name:"WSL2",      mem:"612 MB",cpu:"1.1%", high:false},
    {name:"LM Studio", mem:"4.2 GB", cpu:"12.4%",high:true},
    {name:"Chrome",    mem:"1.1 GB", cpu:"3.2%", high:false},
    {name:"Steam",     mem:"88 MB",  cpu:"0.0%", high:false},
  ];
  return (
    <div>
      <MiniHeader title="Processos Ativos" icon="◉" color={T.violet}/>
      <div style={{padding:"4px 8px",display:"flex",flexDirection:"column",gap:2}}>
        {rows.map((p,i)=>(
          <div key={i} style={{display:"flex",alignItems:"center",gap:4,padding:"5px 6px",background:p.high?`${T.amber}08`:"rgba(255,255,255,.02)",borderRadius:6,border:`1px solid ${p.high?T.amber+"25":"transparent"}`}}>
            <div style={{flex:1}}>
              <div style={mono(8,p.high?T.amber:T.t2)}>{p.name}</div>
              <div style={mono(7,T.t4)}>{p.mem}</div>
            </div>
            <div style={mono(7,p.high?T.amber:T.t3)}>{p.cpu}</div>
            <div style={{width:16,height:16,borderRadius:5,background:`${T.red}15`,border:`1px solid ${T.red}25`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:8,color:T.red,cursor:"pointer"}}>{"✕"}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScreenNotifications() {
  const notes=[
    {icon:"✓", title:"apt upgrade concluído", sub:"42 pacotes ok",         color:T.neon,  time:"agora"},
    {icon:"⬡", title:"Plano criado",           sub:"3 etapas · baixo risco",color:T.indigo,time:"2m"},
    {icon:"⚡", title:"Confirmação pendente",   sub:"sudo systemctl restart",color:T.amber, time:"5m"},
  ];
  return (
    <div>
      <MiniHeader title="Notificações" icon="◈" color={T.cyan} badge="3 novas"/>
      <div style={{padding:"4px 8px",display:"flex",flexDirection:"column",gap:5}}>
        {notes.map((n,i)=>(
          <div key={i} style={{background:T.bg1,border:`1px solid ${n.color}20`,borderRadius:9,padding:"7px 8px",display:"flex",gap:7,alignItems:"flex-start"}}>
            <div style={{width:22,height:22,borderRadius:7,background:`${n.color}18`,border:`1px solid ${n.color}30`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:10,color:n.color,flexShrink:0}}>{n.icon}</div>
            <div style={{flex:1}}>
              <div style={mono(8.5,T.t1,{fontWeight:600})}>{n.title}</div>
              <div style={mono(7.5,T.t3,{marginTop:1})}>{n.sub}</div>
            </div>
            <div style={mono(7,T.t4)}>{n.time}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScreenSysInfo() {
  const bars=[{l:"CPU",v:34,c:T.cyan},{l:"RAM",v:72,c:T.violet},{l:"Disco",v:48,c:T.indigo},{l:"WSL",v:61,c:T.neon}];
  return (
    <div>
      <MiniHeader title="Sysinfo" icon="◆" color={T.indigo}/>
      <div style={{padding:"4px 8px",display:"flex",flexDirection:"column",gap:8}}>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:4}}>
          {[{l:"RAM",v:"16 GB"},{l:"CPU",v:"i5-12th"},{l:"Windows",v:"11 26H2"},{l:"WSL",v:"v2.6.3"}].map((s,i)=>(
            <div key={i} style={{background:T.bg0,borderRadius:7,padding:"5px 7px",border:`1px solid ${T.b2}`}}>
              <div style={mono(7,T.t4)}>{s.l}</div>
              <div style={mono(9,T.t1,{marginTop:2})}>{s.v}</div>
            </div>
          ))}
        </div>
        {bars.map((b,i)=>(
          <div key={i}>
            <div style={{display:"flex",justifyContent:"space-between",marginBottom:3}}>
              <span style={mono(7.5,T.t3)}>{b.l}</span>
              <span style={mono(7.5,b.c)}>{b.v}{"%"}</span>
            </div>
            <div style={{height:3,background:T.bg0,borderRadius:3}}>
              <div style={{width:`${b.v}%`,height:"100%",background:b.c,borderRadius:3,boxShadow:`0 0 6px ${b.c}60`}}/>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScreenPlan() {
  const [approved,setApproved] = useState(false);
  const steps=["Criar C:\\logs\\","Mover 12 arquivos .txt","Verificar integridade"];
  return (
    <div>
      <MiniHeader title="Plano Pendente" icon="⚡" color={T.amber}/>
      <div style={{padding:"4px 8px",display:"flex",flexDirection:"column",gap:6}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center"}}>
          <div style={mono(8,T.t2,{lineHeight:1.6,flex:1})}>{"Criar pasta logs/ e\nmover arquivos .txt"}</div>
          <div style={{background:`${T.neon}15`,border:`1px solid ${T.neon}30`,borderRadius:4,padding:"2px 5px",...mono(7,T.neon),flexShrink:0}}>{"baixo risco"}</div>
        </div>
        {steps.map((s,i)=>(
          <div key={i} style={{display:"flex",gap:6,alignItems:"center",background:"rgba(255,255,255,.02)",borderRadius:7,padding:"5px 7px",border:`1px solid ${T.b2}`}}>
            <div style={{width:15,height:15,borderRadius:"50%",background:`${T.indigo}15`,border:`1px solid ${T.indigo}30`,display:"flex",alignItems:"center",justifyContent:"center",...mono(7,T.indigo)}}>{i+1}</div>
            <span style={mono(8,T.t2)}>{s}</span>
          </div>
        ))}
        {!approved?(
          <div style={{display:"flex",gap:5,marginTop:2}}>
            <button onClick={()=>setApproved(true)} style={{flex:2,padding:"8px 0",background:`linear-gradient(135deg,${T.neon}80,${T.cyan}80)`,border:"none",borderRadius:9,...mono(9,T.bg0,{fontWeight:700}),cursor:"pointer"}}>{"✓ Aprovar"}</button>
            <button style={{flex:1,padding:"8px 0",background:`${T.red}15`,border:`1px solid ${T.red}25`,borderRadius:9,...mono(9,T.red),cursor:"pointer"}}>{"✕"}</button>
          </div>
        ):(
          <div style={{textAlign:"center",padding:"8px 0",...mono(9,T.neon),animation:"fin .3s ease-out"}}>{"✓ Executando..."}</div>
        )}
      </div>
    </div>
  );
}

// ── DESIGN SPEC ────────────────────────────────────────────────────────────
function DesignSpec() {
  const palette=[
    {name:"bg-0",   hex:"#05060a",role:"Base absoluta"},
    {name:"bg-1",   hex:"#0c0d14",role:"Superfície principal"},
    {name:"bg-2",   hex:"#111320",role:"Cards elevados"},
    {name:"indigo", hex:"#6366f1",role:"Acento primário"},
    {name:"violet", hex:"#8b5cf6",role:"Gradiente secundário"},
    {name:"neon",   hex:"#00ff9d",role:"Sucesso / Ativo"},
    {name:"cyan",   hex:"#00d4ff",role:"Dados / Fluxo"},
    {name:"amber",  hex:"#f59e0b",role:"Atenção / Sudo"},
    {name:"red",    hex:"#ff4455",role:"Perigo / Kill"},
  ];
  const comps=[
    {name:"PasswordPrompt", when:"stdout: '[sudo] password'",    color:T.amber,  tags:["Toggle mostrar/ocultar","Enter to send","Amber semântico"]},
    {name:"ConfirmPrompt",  when:"stdout: padrão [Y/n]",         color:T.indigo, tags:["Opção padrão destacada","Scale press feedback","Cor por ação"]},
    {name:"StreamBubble",   when:"stdout fluxo normal",           color:"#3ddc84",tags:["Fundo #060709","Verde terminal","Mono 9.5px"]},
    {name:"ThinkingDots",   when:"processo sem output",           color:T.indigo, tags:["3 dots pulse","Delay escalonado","Label contextual"]},
    {name:"QuickActionsBar",when:"Sempre visível no topo",        color:T.cyan,   tags:["6 ações rápidas","Cor única por ação","Scale on press"]},
    {name:"ToolBadge",      when:"Antes de resposta com tool",    color:T.indigo, tags:["Nome da tool","Cmd truncado","Borda sutil"]},
  ];
  const wsMsgs=[
    {t:"PromptPassword",c:T.amber,  d:"stdout detectado: '[sudo] password'"},
    {t:"PromptConfirm", c:T.indigo, d:"stdout detectado: padrão [Y/n]"},
    {t:"PromptChoice",  c:T.violet, d:"menus numerados no stdout"},
    {t:"PromptInput",   c:T.cyan,   d:"qualquer linha terminando em ':'"},
    {t:"StdinResponse", c:T.neon,   d:"app → daemon → stdin do processo"},
    {t:"StreamChunk",   c:"#3ddc84",d:"output normal em chunks"},
    {t:"Thinking",      c:T.t3,     d:"processo sem output ativo"},
  ];
  return (
    <div style={{maxWidth:720,margin:"0 auto",padding:"40px 32px",display:"flex",flexDirection:"column",gap:28}}>
      <div style={{background:T.bg1,border:`1px solid ${T.b1}`,borderRadius:16,padding:"20px 24px"}}>
        <div style={mono(9,T.indigo,{letterSpacing:2,textTransform:"uppercase",marginBottom:12})}>{"Linguagem Visual — \"Terminal Cyberware\""}</div>
        <div style={mono(11,T.t2,{lineHeight:1.9})}>
          {"A estética do SLED é "}
          <span style={{color:T.indigo}}>{"industrial dark com neon cirúrgico"}</span>
          {" — como uma ferramenta de hacker que foi obsessivamente refinada. Pesada e confiável, mas satisfatória de usar.\n\n"}
          <span style={{color:T.neon}}>{"IBM Plex Mono"}</span>
          {" em 100% do app, sem exceções. Cria coesão total entre o contexto de terminal e a UI.\n\n"}
          {"Cada cor tem papel semântico único: "}
          <span style={{color:T.amber}}>{"amber = sudo/atenção"}</span>
          {", "}
          <span style={{color:T.neon}}>{"neon = sucesso/ativo"}</span>
          {", "}
          <span style={{color:T.red}}>{"red = kill/danger"}</span>
          {", "}
          <span style={{color:T.indigo}}>{"indigo = ação primária"}</span>
          {"."}
        </div>
      </div>

      <div>
        <div style={mono(9,T.indigo,{letterSpacing:3,textTransform:"uppercase",marginBottom:14})}>{"Paleta de Cores"}</div>
        <div style={{display:"flex",flexWrap:"wrap",gap:10}}>
          {palette.map((c,i)=>(
            <div key={i} style={{display:"flex",flexDirection:"column",gap:5,alignItems:"center"}}>
              <div style={{width:44,height:44,borderRadius:12,background:c.hex,border:"1px solid rgba(255,255,255,.1)",boxShadow:i>2?`0 4px 12px ${c.hex}40`:"none"}}/>
              <div style={{textAlign:"center"}}>
                <div style={mono(7.5,T.t2)}>{c.name}</div>
                <div style={mono(7,T.t4)}>{c.hex}</div>
                <div style={mono(6.5,T.t4,{marginTop:1})}>{c.role}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <div style={mono(9,T.indigo,{letterSpacing:3,textTransform:"uppercase",marginBottom:14})}>{"Componentes Interativos"}</div>
        <div style={{display:"flex",flexDirection:"column",gap:8}}>
          {comps.map((c,i)=>(
            <div key={i} style={{background:T.bg1,border:`1px solid ${c.color}20`,borderRadius:10,padding:"12px 14px",display:"flex",gap:12}}>
              <div style={{width:3,background:c.color,borderRadius:3,flexShrink:0}}/>
              <div style={{flex:1}}>
                <div style={mono(11,c.color,{fontWeight:700,marginBottom:4})}>{c.name}</div>
                <div style={mono(8.5,T.t3,{marginBottom:6})}>{"Ativado quando: "}<span style={{color:T.t2}}>{c.when}</span></div>
                <div style={{display:"flex",flexWrap:"wrap",gap:4}}>
                  {c.tags.map((tag,j)=>(
                    <span key={j} style={{...mono(7.5,c.color),background:`${c.color}10`,border:`1px solid ${c.color}20`,borderRadius:4,padding:"2px 6px"}}>{tag}</span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <div style={mono(9,T.indigo,{letterSpacing:3,textTransform:"uppercase",marginBottom:14})}>{"Protocolo WebSocket — Novos Tipos"}</div>
        <div style={{background:T.bg0,border:`1px solid ${T.b1}`,borderRadius:10,padding:16,display:"flex",flexDirection:"column"}}>
          {wsMsgs.map((m,i)=>(
            <div key={i} style={{display:"flex",alignItems:"center",gap:10,padding:"7px 0",borderBottom:i<wsMsgs.length-1?`1px solid ${T.b2}`:"none"}}>
              <span style={{...mono(9,m.c,{fontWeight:600}),background:`${m.c}12`,border:`1px solid ${m.c}25`,borderRadius:4,padding:"2px 7px",flexShrink:0,whiteSpace:"nowrap"}}>{m.t}</span>
              <span style={mono(9,T.t3)}>{m.d}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── ROOT ───────────────────────────────────────────────────────────────────
export default function App() {
  const [tab,setTab] = useState("demo");
  const tabs = [
    {id:"demo",    label:"◉ Demo Interativo"},
    {id:"screens", label:"▣ Telas do App"},
    {id:"design",  label:"◆ Design System"},
  ];
  const flowNodes=[
    {label:"Gemini CLI",sub:"stdout stream",     color:T.violet,icon:"⬡"},
    "→",
    {label:"Daemon Rust",sub:"detect_prompt()",  color:T.indigo,icon:"◆"},
    "→",
    {label:"WebSocket", sub:"WsOutgoing::Prompt",color:T.cyan,  icon:"◈"},
    "→",
    {label:"Android",   sub:"widget interativo", color:T.neon,  icon:"◉"},
    "→",
    {label:"Usuário",   sub:"responde widget",   color:T.amber, icon:"⚡"},
    "→",
    {label:"stdin",     sub:"CLI continua",      color:T.violet,icon:"▶"},
  ];
  const annotations=[
    {icon:"◉",color:T.neon,  title:"Processo Persistente",   desc:"Gemini CLI sobe UMA vez com o daemon. Sem cold start. Mensagens vão direto pro stdin do processo já quente."},
    {icon:"⚡",color:T.amber, title:"Interceptor de Prompts", desc:"Daemon monitora stdout linha por linha. Padrões como '[sudo]', '[Y/n]' geram WsOutgoing tipado em vez de texto bruto."},
    {icon:"◈",color:T.cyan,  title:"Quick Actions",          desc:"6 atalhos para comandos MCP sem digitar. Screenshot, processos, sysinfo e clipboard na barra superior."},
    {icon:"⬡",color:T.indigo,title:"Modo Padrão / Avançado", desc:"Toggle no header. Padrão = MCPs read-only. Avançado = shell + escrita. System prompt diferente por modo."},
    {icon:"▶",color:T.violet,title:"StdinResponse",          desc:"Resposta do usuário vira StdinResponse via WebSocket. Daemon escreve no stdin e o CLI continua de onde parou."},
  ];

  return (
    <div style={{background:T.bg0,minHeight:"100vh",position:"relative",overflow:"hidden"}}>
      <style>{GLOBAL_CSS}</style>
      <div style={{position:"fixed",inset:0,zIndex:0,pointerEvents:"none",backgroundImage:`linear-gradient(${T.b2} 1px,transparent 1px),linear-gradient(90deg,${T.b2} 1px,transparent 1px)`,backgroundSize:"40px 40px"}}/>
      <div style={{position:"fixed",top:"-10%",right:"-5%",width:400,height:400,borderRadius:"50%",background:`${T.indigo}06`,filter:"blur(80px)",pointerEvents:"none",zIndex:0}}/>
      <div style={{position:"fixed",bottom:"-10%",left:"-5%",width:350,height:350,borderRadius:"50%",background:`${T.violet}05`,filter:"blur(80px)",pointerEvents:"none",zIndex:0}}/>

      <div style={{position:"relative",zIndex:1}}>
        {/* HEADER */}
        <div style={{padding:"18px 32px",borderBottom:`1px solid ${T.b1}`,display:"flex",alignItems:"center",gap:16,background:`${T.bg1}cc`,backdropFilter:"blur(12px)",position:"sticky",top:0,zIndex:100}}>
          <div style={{display:"flex",alignItems:"center",gap:10}}>
            <div style={{width:32,height:32,borderRadius:10,background:`linear-gradient(135deg,${T.indigo},${T.violet})`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:15,boxShadow:`0 4px 16px ${T.indigo}50`}}>{"⬡"}</div>
            <div>
              <div style={mono(13,T.t1,{fontWeight:700,letterSpacing:1})}>{"SLED"}</div>
              <div style={mono(8,T.t4)}>{"Android App · Design System"}</div>
            </div>
          </div>
          <div style={{width:1,height:26,background:T.b1}}/>
          <div style={{display:"flex",gap:3}}>
            {tabs.map(t=>(
              <button key={t.id} onClick={()=>setTab(t.id)} style={{padding:"5px 12px",background:tab===t.id?`${T.indigo}18`:"transparent",border:`1px solid ${tab===t.id?T.indigo+"40":"transparent"}`,borderRadius:8,...mono(10,tab===t.id?T.indigo:T.t3),cursor:"pointer",transition:"all .15s"}}>
                {t.label}
              </button>
            ))}
          </div>
          <div style={{marginLeft:"auto",display:"flex",alignItems:"center",gap:8}}>
            <div style={{width:6,height:6,borderRadius:"50%",background:T.neon,animation:"npulse 2s ease-in-out infinite"}}/>
            <span style={mono(8,T.neon)}>{"Gemini CLI · processo persistente"}</span>
          </div>
        </div>

        {tab==="demo"&&(
          <div style={{padding:"44px 32px",display:"flex",gap:44,justifyContent:"center",alignItems:"flex-start",flexWrap:"wrap"}}>
            <div>
              <div style={mono(9,T.t4,{letterSpacing:2,textTransform:"uppercase",marginBottom:18})}>{"Tela principal · Chat + Interação"}</div>
              <PhoneDemo/>
            </div>
            <div style={{maxWidth:340,display:"flex",flexDirection:"column",gap:14,paddingTop:36}}>
              {annotations.map((a,i)=>(
                <div key={i} style={{background:T.bg1,border:`1px solid ${a.color}20`,borderRadius:12,padding:"13px 15px",display:"flex",gap:11,alignItems:"flex-start"}}>
                  <div style={{width:30,height:30,borderRadius:10,background:`${a.color}12`,border:`1px solid ${a.color}25`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:13,color:a.color,flexShrink:0}}>{a.icon}</div>
                  <div>
                    <div style={mono(11,a.color,{fontWeight:700,marginBottom:4})}>{a.title}</div>
                    <div style={mono(10,T.t3,{lineHeight:1.6})}>{a.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {tab==="screens"&&(
          <div style={{padding:"44px 32px"}}>
            <div style={mono(9,T.t4,{letterSpacing:2,textTransform:"uppercase",marginBottom:32,textAlign:"center"})}>{"Telas do app · estados e contextos"}</div>
            <div style={{display:"flex",gap:28,justifyContent:"center",flexWrap:"wrap",alignItems:"flex-start"}}>
              <MiniPhone label="Processos Ativos"><ScreenProcesses/></MiniPhone>
              <MiniPhone label="Notificações"><ScreenNotifications/></MiniPhone>
              <MiniPhone label="Sysinfo · PC-IZAEL"><ScreenSysInfo/></MiniPhone>
              <MiniPhone label="Aprovação de Plano"><ScreenPlan/></MiniPhone>
            </div>
            <div style={{marginTop:48,maxWidth:800,margin:"48px auto 0"}}>
              <div style={mono(9,T.t4,{letterSpacing:2,textTransform:"uppercase",marginBottom:20})}>{"Fluxo stdin/stdout interceptor"}</div>
              <div style={{display:"flex",alignItems:"center",justifyContent:"center",gap:0,flexWrap:"wrap"}}>
                {flowNodes.map((it,i)=>{
                  if (it==="→") return <div key={i} style={mono(16,T.t4,{padding:"0 4px"})}>{"→"}</div>;
                  return (
                    <div key={i} style={{background:T.bg1,border:`1px solid ${it.color}25`,borderRadius:10,padding:"10px 12px",display:"flex",flexDirection:"column",alignItems:"center",gap:4,minWidth:82}}>
                      <span style={{fontSize:15,color:it.color}}>{it.icon}</span>
                      <div style={mono(8.5,it.color,{fontWeight:700,textAlign:"center"})}>{it.label}</div>
                      <div style={mono(7,T.t4,{textAlign:"center"})}>{it.sub}</div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {tab==="design"&&<DesignSpec/>}

        <div style={{borderTop:`1px solid ${T.b1}`,padding:"18px 32px",display:"flex",justifyContent:"space-between",alignItems:"center",background:`${T.bg1}80`}}>
          <span style={mono(8,T.t4)}>{"SLED · Supervised Local Execution Daemon"}</span>
          <div style={{display:"flex",gap:8,flexWrap:"wrap"}}>
            {["Tauri v2","Rust","Kotlin/Compose","Gemini CLI fork","MCP"].map((t,i)=>(
              <span key={i} style={{...mono(7.5,T.t3),background:`${T.indigo}10`,border:`1px solid ${T.indigo}20`,borderRadius:4,padding:"2px 7px"}}>{t}</span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
