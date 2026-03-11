# CoreClash Web Migration (Node.js + PWA + Firebase RealtimeDB)

Este pacote entrega a base da migração do app Android nativo para web application, mantendo a lógica central:

- Estado da partida (turno, contagem de jogadas, streaks, timeouts).
- Skills Triângulo e Quadrado com regra de unlock e ownership.
- Células fantasma (ghost) e validação de vitória ignorando células ghost.
- IA com níveis BEGINNER, MODERATE e GAME_MASTER (minimax para master).
- Backend Node.js para matchmaking e sincronização de estado.
- PWA instalável com service worker e manifesto.
- Persistência online via Firebase Realtime Database.

## Estrutura

- `shared/gameEngine.js`: lógica portada de `GameState`, `GameManager`, `Cell` e decisões do bot.
- `backend/`: API Node.js (Express + Firebase Admin).
- `frontend/`: app web/PWA (HTML/CSS/JS) usando o engine compartilhado.

## Configuração

### 1) Backend

```bash
cd web/backend
npm install
export FIREBASE_DATABASE_URL="https://SEU-PROJETO.firebaseio.com"
export GOOGLE_APPLICATION_CREDENTIALS="/caminho/service-account.json"
npm run start
```

### 2) Frontend

Defina `window.__FIREBASE_CONFIG__` antes de carregar `app.js` (por exemplo em um `<script>` no `index.html`) com as credenciais web do Firebase.

```bash
cd web/frontend
npm install
npm run start
```

## Endpoints principais

- `POST /api/matchmaking/enqueue`
- `GET /api/matches/:matchId`
- `POST /api/matches/:matchId/sync`
- `POST /api/engine/play`

## Próximos passos para paridade 100%

1. Migrar todos os overlays/animadores (`MatchIntro`, `VictoryOverlay`, `TurnHud`) para componentes web.
2. Portar autenticação social completa do Android (`Google Sign-In`) para Firebase Auth web.
3. Portar loja/economia completa (`StoreManager`, `Wallet`, compras).
4. Portar todos os estilos de símbolo/tabuleiro e efeitos visuais em Canvas/WebGL.
5. Cobrir fluxo online completo (convites, presença, reconexão, abandono e punição por timeout).
