package com.example.coreclash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import game.GameState;

public class ExampleUnitTest {

    @Test
    public void square_skill_is_locked_until_unlock_move() {
        GameState state = new GameState();

        assertFalse(state.canUseSquare());

        for (int i = 0; i < state.getSquareUnlockMove(); i++) {
            state.addMove();
        }

        assertTrue(state.canUseSquare());
    }

    @Test
    public void ghost_telemetry_accumulates_with_positive_values_only() {
        GameState state = new GameState();
        state.addGhosts(3);
        state.addGhosts(-5);

        assertEquals(3, state.getGhostCount());
    }

    @Test
    public void ranked_mode_awards_points_on_win() {
        GameState state = new GameState();
        state.setGameMode(GameState.GameMode.RANKED);
        state.addMove();
        state.addMove();
        state.addGhosts(2);

        state.registerWin();

        assertTrue(state.getRankedPoints() >= 3);
        assertEquals(1, state.getTotalWins());
        assertEquals(1, state.getWinStreak());
    }
}
