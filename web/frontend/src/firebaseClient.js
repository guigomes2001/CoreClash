import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import { getDatabase, ref, onValue, set } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-database.js';

let db = null;

export function initFirebase(config) {
  if (!config?.apiKey) return null;
  const app = initializeApp(config);
  db = getDatabase(app);
  return db;
}

export function watchMatch(matchId, cb) {
  if (!db) return () => {};
  const r = ref(db, `web/matches/${matchId}`);
  return onValue(r, (snap) => cb(snap.val()));
}

export async function writeMatch(matchId, payload) {
  if (!db) return;
  await set(ref(db, `web/matches/${matchId}/engineSnapshot`), payload);
}
