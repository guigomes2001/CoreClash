package com.example.coreclash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import game.GameState;

public class ExampleUnitTest {

    @Test
    public void square_skill_starts_available_when_exists() {
        GameState state = new GameState();
        assertTrue(state.canUseSquare());
    }

    @Test
    public void ghost_telemetry_accumulates_with_positive_values_only() {
        GameState state = new GameState();
        state.addGhosts(3);
        state.addGhosts(-5);

        assertEquals(3, state.getGhostCount());
    }
}
