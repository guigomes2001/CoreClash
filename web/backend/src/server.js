import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { GameEngine } from '../../shared/gameEngine.js';
import { enqueuePlayer, getMatch, saveMatchState, tryCreateMatch } from './matchService.js';

const app = express();
app.use(cors());
app.use(express.json());

app.get('/health', (_, res) => res.json({ ok: true }));

app.post('/api/matchmaking/enqueue', async (req, res) => {
  const { uid, displayName, difficulty = 'MODERATE' } = req.body;
  if (!uid || !displayName) return res.status(400).json({ error: 'uid e displayName são obrigatórios' });

  await enqueuePlayer({ uid, displayName, difficulty });
  const created = await tryCreateMatch(uid);
  return res.json({ queued: !created, match: created ?? null });
});

app.get('/api/matches/:matchId', async (req, res) => {
  const match = await getMatch(req.params.matchId);
  if (!match) return res.status(404).json({ error: 'Partida não encontrada' });
  return res.json(match);
});

app.post('/api/matches/:matchId/sync', async (req, res) => {
  const { engineSnapshot, currentTurn, status } = req.body;
  if (!engineSnapshot || !currentTurn) {
    return res.status(400).json({ error: 'engineSnapshot e currentTurn são obrigatórios' });
  }
  await saveMatchState(req.params.matchId, { engineSnapshot, currentTurn, status });
  return res.json({ ok: true });
});

app.post('/api/engine/play', (req, res) => {
  const { snapshot, action } = req.body;
  const engine = new GameEngine();
  if (snapshot) engine.hydrate(snapshot);

  let result;
  if (action.type === 'MOVE') {
    result = engine.play(action.row, action.col);
  } else if (action.type === 'TRIANGLE') {
    result = engine.useTriangle();
  } else if (action.type === 'SQUARE') {
    result = engine.useSquare();
  } else {
    return res.status(400).json({ error: 'Ação inválida' });
  }

  return res.json({ result, snapshot: engine.serialize() });
});

const port = process.env.PORT || 8080;
app.listen(port, () => {
  console.log(`CoreClash web backend rodando na porta ${port}`);
});
