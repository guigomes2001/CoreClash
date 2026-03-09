package com.coreclash.webbackend.service;

import java.util.List;

public final class GameRules {
  private GameRules() {}

  public static String winner(List<String> board) {
    int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
    for (int[] line : lines) {
      String a = board.get(line[0]);
      String b = board.get(line[1]);
      String c = board.get(line[2]);
      if (!a.isBlank() && a.equals(b) && b.equals(c)) return a;
    }
    return null;
  }
}
