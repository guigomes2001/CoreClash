import { nanoid } from 'nanoid';
import { db } from './firebase.js';

const queueRef = db.ref('web/matchmakingQueue');
const matchesRef = db.ref('web/matches');

export async function enqueuePlayer({ uid, displayName, difficulty }) {
  const entryRef = queueRef.child(uid);
  await entryRef.set({ uid, displayName, difficulty, joinedAt: Date.now() });
  return entryRef.key;
}

export async function tryCreateMatch(uid) {
  const queueSnap = await queueRef.orderByChild('joinedAt').limitToFirst(10).get();
  if (!queueSnap.exists()) return null;

  const players = Object.values(queueSnap.val()).filter((p) => p.uid !== uid);
  if (players.length === 0) return null;

  const opponent = players[0];
  const meSnap = await queueRef.child(uid).get();
  if (!meSnap.exists()) return null;

  const me = meSnap.val();
  const matchId = nanoid(12);
  const match = {
    matchId,
    players: {
      X: { uid: me.uid, displayName: me.displayName },
      O: { uid: opponent.uid, displayName: opponent.displayName },
    },
    status: 'LOBBY',
    createdAt: Date.now(),
    currentTurn: 'X',
    engineSnapshot: null,
  };

  await Promise.all([
    matchesRef.child(matchId).set(match),
    queueRef.child(uid).remove(),
    queueRef.child(opponent.uid).remove(),
  ]);

  return match;
}

export async function saveMatchState(matchId, payload) {
  await matchesRef.child(matchId).update({
    engineSnapshot: payload.engineSnapshot,
    currentTurn: payload.currentTurn,
    status: payload.status ?? 'RUNNING',
    updatedAt: Date.now(),
  });
}

export async function getMatch(matchId) {
  const snap = await matchesRef.child(matchId).get();
  return snap.exists() ? snap.val() : null;
}
