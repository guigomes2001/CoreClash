package com.example.coreclash;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import game.GameState;

public class GameStateProgressionTest {

    @Test
    public void ranked_win_awards_points_with_speed_bonus_and_ghost_penalty() {
        GameState state = new GameState();
        state.setGameMode(GameState.GameMode.RANKED);
        for (int i = 0; i < 5; i++) {
            state.addMove();
        }
        state.addGhosts(4);

        state.registerWin();

        // base 10 + speed (9 - 5 = 4) - ghost penalty (4 / 2 = 2) = 12
        assertEquals(12, state.getRankedPoints());
        assertEquals(1, state.getTotalWins());
        assertEquals(1, state.getWinStreak());
    }

    @Test
    public void ranked_loss_deducts_points_but_never_below_zero() {
        GameState state = new GameState();
        state.setGameMode(GameState.GameMode.RANKED);
        state.restoreProgress(2, 2, 2, 4);

        state.registerLoss();

        assertEquals(0, state.getRankedPoints());
        assertEquals(0, state.getWinStreak());
    }

    @Test
    public void casual_loss_keeps_ranked_points() {
        GameState state = new GameState();
        state.restoreProgress(2, 2, 2, 50);

        state.registerLoss();

        assertEquals(50, state.getRankedPoints());
        assertEquals(0, state.getWinStreak());
    }

    @Test
    public void restore_progress_hydrates_persisted_stats() {
        GameState state = new GameState();

        state.restoreProgress(7, 2, 5, 90);

        assertEquals(7, state.getTotalWins());
        assertEquals(2, state.getWinStreak());
        assertEquals(5, state.getBestWinStreak());
        assertEquals(90, state.getRankedPoints());
        assertEquals("CORE ELITE", state.getRankLabel());
    }

    @Test
    public void restore_progress_never_lets_best_streak_fall_below_current() {
        GameState state = new GameState();

        state.restoreProgress(3, 4, 1, 0);

        assertEquals(4, state.getBestWinStreak());
    }

    @Test
    public void match_reset_keeps_lifetime_progression() {
        GameState state = new GameState();
        state.restoreProgress(7, 3, 5, 90);
        state.addMove();
        state.addGhosts(2);

        state.reset();

        assertEquals(0, state.getMoveCount());
        assertEquals(0, state.getGhostCount());
        assertEquals(7, state.getTotalWins());
        assertEquals(3, state.getWinStreak());
        assertEquals(90, state.getRankedPoints());
    }
}
