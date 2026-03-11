import test from 'node:test';
import assert from 'node:assert/strict';
import { GameEngine } from '../../shared/gameEngine.js';

test('detecta vitória em linha', () => {
  const e = new GameEngine();
  e.play(0,0); // X
  e.play(1,0); // O
  e.play(0,1); // X
  e.play(1,1); // O
  const result = e.play(0,2); // X win
  assert.equal(result.winner, 'X');
  assert.equal(e.isGameOver, true);
});

test('triângulo transforma células em ghost', () => {
  const e = new GameEngine();
  e.play(0,1); // X
  e.play(1,1); // O
  e.play(2,2); // X
  e.play(1,2); // O
  e.play(2,0); // X
  const out = e.useTriangle();
  assert.equal(out.ok, true);
  assert.equal(e.board[0][1].ghost, true);
  assert.equal(e.board[2][2].ghost, true);
  assert.equal(e.board[2][0].ghost, true);
});
