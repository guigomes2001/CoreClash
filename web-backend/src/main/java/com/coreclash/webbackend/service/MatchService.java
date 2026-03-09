package com.coreclash.webbackend.service;

import com.coreclash.webbackend.model.MatchState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class MatchService {
  private final FirebaseDatabase firebaseDatabase;

  public MatchService(FirebaseDatabase firebaseDatabase) {
    this.firebaseDatabase = firebaseDatabase;
  }

  public MatchState createMatch() {
    MatchState state = new MatchState();
    state.setId(UUID.randomUUID().toString().replace("-", ""));
    ref(state.getId()).setValueAsync(state);
    return state;
  }

  public MatchState getMatch(String id) {
    CompletableFuture<MatchState> future = new CompletableFuture<>();
    ref(id).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override public void onDataChange(DataSnapshot snapshot) {
        future.complete(snapshot.getValue(MatchState.class));
      }
      @Override public void onCancelled(DatabaseError error) {
        future.completeExceptionally(new IllegalStateException(error.getMessage()));
      }
    });
    return future.join();
  }

  public MatchState applyMove(String id, int index, String player) {
    MatchState state = getMatch(id);
    if (state == null || state.isEnded()) throw new IllegalStateException("Partida inválida");
    if (index < 0 || index > 8) throw new IllegalArgumentException("Índice inválido");
    if (!state.getTurn().equals(player)) throw new IllegalStateException("Não é o turno desse jogador");
    if (!state.getBoard().get(index).isBlank()) throw new IllegalStateException("Casa ocupada");

    state.getBoard().set(index, player);
    String winner = GameRules.winner(state.getBoard());
    if (winner != null || state.getBoard().stream().noneMatch(String::isBlank)) {
      state.setEnded(true);
    } else {
      state.setTurn("X".equals(state.getTurn()) ? "O" : "X");
    }
    ref(id).setValueAsync(state);
    return state;
  }

  private DatabaseReference ref(String id) {
    return firebaseDatabase.getReference("coreclash/matches/").child(id);
  }
}
