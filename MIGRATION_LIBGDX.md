# Core Clash — Migração incremental para LibGDX

## Objetivo
Melhorar qualidade de animações/render sem quebrar o app Android atual.

## Estratégia em fases
1. **Scaffold** (este PR)
   - módulo `:game-core` com runtime LibGDX.
   - portas (`MatchStatePort`) para desacoplar lógica/render.
2. **Bridge real**
   - adaptar `GameManager`/`GameState` atuais para `MatchStatePort`.
3. **Render de partida**
   - tabuleiro, skills e linha de vitória no `MatchScreen`.
4. **UX completa**
   - menu e overlays migrados para `Scene2D`.

## Regras de arquitetura
- Lógica de domínio não depende de Android UI.
- `:app` hospeda plataforma; `:game-core` renderiza e anima.
- Migração por feature flags para rollback simples.
