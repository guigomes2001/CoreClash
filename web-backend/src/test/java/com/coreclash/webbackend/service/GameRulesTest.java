package com.coreclash.webbackend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameRulesTest {
  @Test
  void shouldDetectWinner() {
    String winner = GameRules.winner(List.of("X","X","X","","","","","",""));
    assertEquals("X", winner);
  }

  @Test
  void shouldReturnNullWithoutWinner() {
    String winner = GameRules.winner(List.of("X","O","X","X","O","O","O","X","X"));
    assertNull(winner);
  }
}
