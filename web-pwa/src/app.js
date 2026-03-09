const WIN_LINES = [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
const API_BASE = window.CORECLASH_API_BASE || 'http://localhost:8080/api';

const state = {
  mode: 'local',
  roomId: null,
  board: Array(9).fill(null),
  turn: 'X',
  score: {X:0,O:0},
  players: {X:'Player X', O:'Player O'},
  ended: false,
};

const ui = {
  board: document.getElementById('board'), turnText: document.getElementById('turnText'), scoreText: document.getElementById('scoreText'),
  playerX: document.getElementById('playerX'), playerO: document.getElementById('playerO'),
  newMatchBtn: document.getElementById('newMatchBtn'), resetScoreBtn: document.getElementById('resetScoreBtn'),
  modeLocal: document.getElementById('modeLocal'), modeOnline: document.getElementById('modeOnline'),
  onlinePanel: document.getElementById('onlinePanel'), roomId: document.getElementById('roomId'),
  createRoomBtn: document.getElementById('createRoomBtn'), joinRoomBtn: document.getElementById('joinRoomBtn'), onlineStatus: document.getElementById('onlineStatus'),
  installBtn: document.getElementById('installBtn'),
};

const saveLocal = () => localStorage.setItem('coreclash-web-state', JSON.stringify({score:state.score, players:state.players}));
const loadLocal = () => {
  try { const v = JSON.parse(localStorage.getItem('coreclash-web-state') || '{}');
    if (v.score) state.score = v.score; if (v.players) state.players = v.players;
  } catch {}
};

const evaluate = (board) => {
  for (const [a,b,c] of WIN_LINES) if (board[a] && board[a] === board[b] && board[a] === board[c]) return {winner: board[a], line:[a,b,c]};
  return board.every(Boolean) ? {draw:true} : null;
};

async function createRoom() {
  const resp = await fetch(`${API_BASE}/matches`, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({hostName: state.players.X})});
  if (!resp.ok) throw new Error('Erro ao criar sala');
  const data = await resp.json();
  state.roomId = data.id;
  ui.roomId.value = data.id;
  await refreshRoom();
}

async function joinRoom() {
  if (!ui.roomId.value.trim()) throw new Error('Informe a sala');
  state.roomId = ui.roomId.value.trim();
  await refreshRoom();
}

async function sendMove(index) {
  if (!state.roomId) return;
  const resp = await fetch(`${API_BASE}/matches/${state.roomId}/moves`, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({index, player: state.turn})});
  if (!resp.ok) throw new Error('Falha no envio da jogada');
  await refreshRoom();
}

async function refreshRoom() {
  if (!state.roomId) return;
  const resp = await fetch(`${API_BASE}/matches/${state.roomId}`);
  if (!resp.ok) throw new Error('Sala não encontrada');
  const data = await resp.json();
  state.board = data.board;
  state.turn = data.turn;
  state.ended = !!data.ended;
  ui.onlineStatus.textContent = `Conectado na sala ${state.roomId}`;
  render();
}

function render() {
  const result = evaluate(state.board);
  const xName = state.players.X || 'Player X'; const oName = state.players.O || 'Player O';
  if (result?.winner) ui.turnText.textContent = `${state.players[result.winner]} venceu!`;
  else if (result?.draw) ui.turnText.textContent = 'Empate!';
  else ui.turnText.textContent = `Vez de ${state.players[state.turn]} (${state.turn})`;
  ui.scoreText.textContent = `${xName} ${state.score.X} x ${state.score.O} ${oName}`;
  [...ui.board.children].forEach((cell, index) => {
    const value = state.board[index];
    cell.dataset.symbol = value || ''; cell.textContent = value || ''; cell.classList.remove('win');
    if (result?.line?.includes(index)) cell.classList.add('win');
  });
  saveLocal();
}

function onPlay(event) {
  const index = Number(event.currentTarget.dataset.index);
  if (state.ended || state.board[index]) return;
  if (state.mode === 'online') return sendMove(index).catch(e => ui.onlineStatus.textContent = e.message);

  state.board[index] = state.turn;
  const result = evaluate(state.board);
  if (result?.winner) { state.score[result.winner] += 1; state.ended = true; }
  else if (result?.draw) state.ended = true;
  else state.turn = state.turn === 'X' ? 'O' : 'X';
  render();
}

function resetMatch() { state.board = Array(9).fill(null); state.turn = 'X'; state.ended = false; render(); }

function createBoard() {
  ui.board.innerHTML = '';
  state.board.forEach((_, i) => { const b=document.createElement('button'); b.className='cell'; b.dataset.index=String(i); b.addEventListener('click', onPlay); ui.board.appendChild(b); });
}

function setMode(mode){
  state.mode = mode;
  ui.modeLocal.classList.toggle('active', mode === 'local');
  ui.modeOnline.classList.toggle('active', mode === 'online');
  ui.onlinePanel.hidden = mode !== 'online';
  ui.onlineStatus.textContent = mode === 'online' ? 'Pronto para conectar' : 'Offline';
  resetMatch();
}

ui.playerX.addEventListener('input', e => {state.players.X = e.target.value.trim() || 'Player X'; render();});
ui.playerO.addEventListener('input', e => {state.players.O = e.target.value.trim() || 'Player O'; render();});
ui.newMatchBtn.addEventListener('click', resetMatch);
ui.resetScoreBtn.addEventListener('click', ()=>{ state.score={X:0,O:0}; resetMatch(); });
ui.modeLocal.addEventListener('click', ()=>setMode('local'));
ui.modeOnline.addEventListener('click', ()=>setMode('online'));
ui.createRoomBtn.addEventListener('click', ()=>createRoom().catch(e => ui.onlineStatus.textContent = e.message));
ui.joinRoomBtn.addEventListener('click', ()=>joinRoom().catch(e => ui.onlineStatus.textContent = e.message));

let deferredPrompt = null;
window.addEventListener('beforeinstallprompt', (event) => { event.preventDefault(); deferredPrompt = event; ui.installBtn.hidden = false; });
ui.installBtn.addEventListener('click', async () => { if (!deferredPrompt) return; deferredPrompt.prompt(); deferredPrompt = null; ui.installBtn.hidden = true; });
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./service-worker.js').catch(() => {});

loadLocal();
createBoard();
render();
setInterval(() => { if (state.mode === 'online' && state.roomId) refreshRoom().catch(()=>{}); }, 2000);
