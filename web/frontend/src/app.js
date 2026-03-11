import { GameEngine, chooseBotMove } from './gameEngine.js';
import { initFirebase } from './firebaseClient.js';

const engine = new GameEngine();
const boardEl = document.getElementById('board');
const statusEl = document.getElementById('status');
const hudEl = document.getElementById('hud');
const modeEl = document.getElementById('mode');
const difficultyEl = document.getElementById('difficulty');

const cells = [];

const firebaseConfig = window.__FIREBASE_CONFIG__;
initFirebase(firebaseConfig);

for (let r = 0; r < 3; r++) {
  for (let c = 0; c < 3; c++) {
    const btn = document.createElement('button');
    btn.className = 'cell';
    btn.addEventListener('click', () => playTurn(r, c));
    boardEl.appendChild(btn);
    cells.push(btn);
  }
}

function mapSymbol(symbol) {
  return symbol === 'X' ? '✕' : symbol === 'O' ? '◉' : '';
}

function render() {
  let index = 0;
  for (let r = 0; r < 3; r++) {
    for (let c = 0; c < 3; c++) {
      const cell = engine.board[r][c];
      const el = cells[index++];
      el.textContent = mapSymbol(cell.symbol);
      el.classList.toggle('ghost', cell.ghost);
      el.classList.toggle('x', cell.symbol === 'X');
      el.classList.toggle('o', cell.symbol === 'O');
    }
  }

  hudEl.textContent = `Turno: ${engine.getCurrentPlayerSymbol()} | Jogadas: ${engine.state.moveCount} | Fantasmas: ${engine.state.ghostCount}`;
  if (engine.isGameOver) {
    statusEl.textContent = engine.lastWins.length ? 'Vitória!' : 'Empate!';
  } else {
    statusEl.textContent = 'Partida em andamento';
  }
}

function maybePlayBot() {
  if (modeEl.value !== 'BOT' || engine.isGameOver || engine.getCurrentPlayerSymbol() !== 'O') return;

  const shouldSkill = Math.random() < (difficultyEl.value === 'BEGINNER' ? 0.2 : difficultyEl.value === 'MODERATE' ? 0.55 : 0.8);
  if (shouldSkill) {
    const used = Math.random() > 0.5 ? engine.useTriangle() : engine.useSquare();
    if (used.ok) {
      render();
      return;
    }
  }

  const move = chooseBotMove(engine, difficultyEl.value);
  if (move) {
    engine.play(move[0], move[1]);
    render();
  }
}

function playTurn(r, c) {
  const result = engine.play(r, c);
  if (!result.ok) return;
  render();
  setTimeout(maybePlayBot, 450);
}

document.getElementById('startBtn').addEventListener('click', () => {
  engine.reset();
  render();
});

document.getElementById('triangleBtn').addEventListener('click', () => {
  engine.useTriangle();
  render();
  setTimeout(maybePlayBot, 450);
});

document.getElementById('squareBtn').addEventListener('click', () => {
  engine.useSquare();
  render();
  setTimeout(maybePlayBot, 450);
});

document.getElementById('resetBtn').addEventListener('click', () => {
  engine.reset();
  render();
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/public/sw.js');
}

render();
