package com.example.coreclash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import game.GameState;

public class ExampleUnitTest {

    @Test
    public void square_skill_unlocks_only_after_fourth_move() {
        GameState state = new GameState();
        assertFalse(state.canUseSquare());

        for (int i = 0; i < state.getSquareUnlockMove(); i++) {
            state.addMove();
        }
        assertTrue(state.canUseSquare());
    }

    @Test
    public void triangle_skill_unlocks_only_after_third_move() {
        GameState state = new GameState();
        assertFalse(state.canUseTriangle());

        for (int i = 0; i < state.getTriangleUnlockMove(); i++) {
            state.addMove();
        }
        assertTrue(state.canUseTriangle());
    }

    @Test
    public void ghost_telemetry_accumulates_with_positive_values_only() {
        GameState state = new GameState();
        state.addGhosts(3);
        state.addGhosts(-5);

        assertEquals(3, state.getGhostCount());
    }
}
