package com.coreclash.webbackend.model;

import java.util.ArrayList;
import java.util.List;

public class MatchState {
  private String id;
  private List<String> board = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
  private String turn = "X";
  private boolean ended = false;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public List<String> getBoard() { return board; }
  public void setBoard(List<String> board) { this.board = board; }
  public String getTurn() { return turn; }
  public void setTurn(String turn) { this.turn = turn; }
  public boolean isEnded() { return ended; }
  public void setEnded(boolean ended) { this.ended = ended; }
}
