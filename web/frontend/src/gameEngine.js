const TRIANGLE_CELLS = [[0,1],[2,2],[2,0]];

export class Cell {
  constructor() {
    this.symbol = '';
    this.ghost = false;
  }

  isEmpty() { return this.symbol === ''; }
  isGhost() { return this.ghost; }
  getVisualSymbol() { return this.symbol; }

  setSymbol(symbol) {
    this.symbol = symbol;
    this.ghost = false;
  }

  turnIntoGhost() {
    if (!this.isEmpty()) this.ghost = true;
  }

  reset() {
    this.symbol = '';
    this.ghost = false;
  }
}

export class GameState {
  static TRIANGLE_UNLOCK_MOVE = 3;
  static SQUARE_UNLOCK_MOVE = 4;

  constructor() {
    this.xTurn = true;
    this.moveCount = 0;
    this.ghostCount = 0;
    this.triangleExists = true;
    this.squareExists = true;
    this.triangleOwnerIsX = null;
    this.squareOwnerIsX = null;
    this.tutorialSkillOverride = false;
    this.totalWins = 0;
    this.winStreak = 0;
    this.bestWinStreak = 0;
    this.timeoutStreakX = 0;
    this.timeoutStreakO = 0;
  }

  nextTurn() { this.xTurn = !this.xTurn; }
  addMove() { this.moveCount += 1; }
  addGhosts(amount) { this.ghostCount += Math.max(0, amount); }

  canUseTriangle() {
    if (!this.triangleExists) return false;
    if (this.tutorialSkillOverride) return true;
    if (this.moveCount < GameState.TRIANGLE_UNLOCK_MOVE) return false;
    return this.triangleOwnerIsX === null || this.triangleOwnerIsX === this.xTurn;
  }

  canUseSquare() {
    if (!this.squareExists) return false;
    if (this.tutorialSkillOverride) return true;
    if (this.moveCount < GameState.SQUARE_UNLOCK_MOVE) return false;
    return this.squareOwnerIsX === null || this.squareOwnerIsX === this.xTurn;
  }

  triggerTriangleUsed() {
    this.triangleExists = false;
    if (this.squareExists) this.squareOwnerIsX = !this.xTurn;
  }

  triggerSquareUsed() {
    this.squareExists = false;
    if (this.triangleExists) this.triangleOwnerIsX = !this.xTurn;
  }

  registerWin() {
    this.totalWins += 1;
    this.winStreak += 1;
    this.bestWinStreak = Math.max(this.bestWinStreak, this.winStreak);
  }

  registerLossOrDraw() {
    this.winStreak = 0;
  }

  reset() {
    this.xTurn = true;
    this.moveCount = 0;
    this.ghostCount = 0;
    this.triangleExists = true;
    this.squareExists = true;
    this.triangleOwnerIsX = null;
    this.squareOwnerIsX = null;
    this.tutorialSkillOverride = false;
    this.timeoutStreakX = 0;
    this.timeoutStreakO = 0;
  }
}

export class GameEngine {
  constructor() {
    this.state = new GameState();
    this.board = Array.from({ length: 3 }, () => Array.from({ length: 3 }, () => new Cell()));
    this.isGameOver = false;
    this.lastWins = [];
  }

  getCurrentPlayerSymbol() {
    return this.state.xTurn ? 'X' : 'O';
  }

  serialize() {
    return {
      state: { ...this.state },
      board: this.board.map(row => row.map(c => ({ symbol: c.symbol, ghost: c.ghost }))),
      isGameOver: this.isGameOver,
      lastWins: this.lastWins,
    };
  }

  hydrate(snapshot) {
    Object.assign(this.state, snapshot.state);
    this.isGameOver = snapshot.isGameOver;
    this.lastWins = snapshot.lastWins || [];
    snapshot.board.forEach((row, r) => row.forEach((cell, c) => {
      this.board[r][c].symbol = cell.symbol;
      this.board[r][c].ghost = cell.ghost;
    }));
  }

  play(row, col) {
    if (this.isGameOver) return { ok: false, reason: 'game_over' };
    const cell = this.board[row][col];
    if (!cell.isEmpty() && !cell.isGhost()) return { ok: false, reason: 'occupied' };

    this.state.addMove();
    cell.setSymbol(this.getCurrentPlayerSymbol());

    if (this.checkWinner()) {
      this.isGameOver = true;
      this.state.registerWin();
      return { ok: true, winner: this.getCurrentPlayerSymbol(), wins: this.lastWins };
    }

    if (this.isBoardFull()) {
      this.isGameOver = true;
      this.state.registerLossOrDraw();
      return { ok: true, draw: true };
    }

    this.state.nextTurn();
    return { ok: true };
  }

  useTriangle() {
    if (this.isGameOver || !this.state.canUseTriangle()) return { ok: false };
    let affected = 0;
    TRIANGLE_CELLS.forEach(([r, c]) => {
      const cell = this.board[r][c];
      if (!cell.isEmpty() && !cell.isGhost()) {
        cell.turnIntoGhost();
        affected += 1;
      }
    });
    if (!this.state.tutorialSkillOverride && affected === 0) return { ok: false };
    this.state.addGhosts(affected);
    this.state.triggerTriangleUsed();
    this.state.nextTurn();
    return { ok: true, affected };
  }

  useSquare() {
    if (this.isGameOver || !this.state.canUseSquare()) return { ok: false };
    let affected = 0;
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 3; c++) {
        if (r === 1 && c === 1) continue;
        const cell = this.board[r][c];
        if (!cell.isEmpty() && !cell.isGhost()) {
          cell.turnIntoGhost();
          affected += 1;
        }
      }
    }
    if (!this.state.tutorialSkillOverride && affected === 0) return { ok: false };
    this.state.addGhosts(affected);
    this.state.triggerSquareUsed();
    this.state.nextTurn();
    return { ok: true, affected };
  }

  getAvailableMoves() {
    const moves = [];
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 3; c++) {
        const cell = this.board[r][c];
        if (cell.isEmpty() || cell.isGhost()) moves.push([r, c]);
      }
    }
    return moves;
  }

  findWinningMoveFor(symbol) {
    for (const [r, c] of this.getAvailableMoves()) {
      const cell = this.board[r][c];
      const old = { symbol: cell.symbol, ghost: cell.ghost };
      cell.setSymbol(symbol);
      const win = this.checkWinnerBySymbol(symbol);
      cell.symbol = old.symbol;
      cell.ghost = old.ghost;
      if (win) return [r, c];
    }
    return null;
  }

  findBestMoveForO() {
    let bestScore = -Infinity;
    let bestMove = null;

    for (const [r, c] of this.getAvailableMoves()) {
      const cell = this.board[r][c];
      const old = { symbol: cell.symbol, ghost: cell.ghost };
      cell.setSymbol('O');
      const score = this.minimax(false, 0);
      cell.symbol = old.symbol;
      cell.ghost = old.ghost;
      if (score > bestScore) {
        bestScore = score;
        bestMove = [r, c];
      }
    }
    return bestMove;
  }

  minimax(maximizing, depth) {
    if (this.checkWinnerBySymbol('O')) return 10 - depth;
    if (this.checkWinnerBySymbol('X')) return depth - 10;
    if (this.getAvailableMoves().length === 0) return 0;

    let best = maximizing ? -Infinity : Infinity;
    for (const [r, c] of this.getAvailableMoves()) {
      const cell = this.board[r][c];
      cell.setSymbol(maximizing ? 'O' : 'X');
      const score = this.minimax(!maximizing, depth + 1);
      cell.reset();
      best = maximizing ? Math.max(best, score) : Math.min(best, score);
    }
    return best;
  }

  checkWinner() {
    this.lastWins = [];
    for (let i = 0; i < 3; i++) {
      if (this.checkLine(i,0,i,1,i,2)) this.lastWins.push([i,0,i,2]);
      if (this.checkLine(0,i,1,i,2,i)) this.lastWins.push([0,i,2,i]);
    }
    if (this.checkLine(0,0,1,1,2,2)) this.lastWins.push([0,0,2,2]);
    if (this.checkLine(0,2,1,1,2,0)) this.lastWins.push([0,2,2,0]);
    return this.lastWins.length > 0;
  }

  checkWinnerBySymbol(symbol) {
    for (let i = 0; i < 3; i++) {
      if (this.lineOwnedBy(symbol, i,0,i,1,i,2)) return true;
      if (this.lineOwnedBy(symbol, 0,i,1,i,2,i)) return true;
    }
    return this.lineOwnedBy(symbol,0,0,1,1,2,2) || this.lineOwnedBy(symbol,0,2,1,1,2,0);
  }

  lineOwnedBy(symbol, r1,c1,r2,c2,r3,c3) {
    const a = this.board[r1][c1], b = this.board[r2][c2], c = this.board[r3][c3];
    return !a.ghost && !b.ghost && !c.ghost && a.symbol===symbol && b.symbol===symbol && c.symbol===symbol;
  }

  checkLine(r1,c1,r2,c2,r3,c3) {
    const a = this.board[r1][c1], b = this.board[r2][c2], c = this.board[r3][c3];
    if (a.ghost||b.ghost||c.ghost||a.isEmpty()||b.isEmpty()||c.isEmpty()) return false;
    return a.symbol === b.symbol && b.symbol === c.symbol;
  }

  isBoardFull() {
    for (const row of this.board) {
      for (const cell of row) {
        if (cell.isEmpty() || cell.isGhost()) return false;
      }
    }
    return true;
  }

  reset() {
    this.isGameOver = false;
    this.lastWins = [];
    this.board.flat().forEach(c => c.reset());
    this.state.reset();
  }
}

export function chooseBotMove(engine, difficulty = 'BEGINNER') {
  const moves = engine.getAvailableMoves();
  if (moves.length === 0) return null;

  if (difficulty === 'BEGINNER') {
    return moves[Math.floor(Math.random() * moves.length)];
  }

  const win = engine.findWinningMoveFor('O');
  if (win) return win;

  const block = engine.findWinningMoveFor('X');
  if (block) return block;

  if (difficulty === 'MODERATE') {
    const center = moves.find(([r,c]) => r===1 && c===1);
    return center ?? moves[Math.floor(Math.random() * moves.length)];
  }

  return engine.findBestMoveForO() ?? moves[Math.floor(Math.random() * moves.length)];
}
