package com.coreclash.webbackend.controller;

import com.coreclash.webbackend.model.CreateMatchRequest;
import com.coreclash.webbackend.model.MatchState;
import com.coreclash.webbackend.model.MoveRequest;
import com.coreclash.webbackend.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "*")
public class MatchController {
  private final MatchService matchService;

  public MatchController(MatchService matchService) {
    this.matchService = matchService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchState create(@RequestBody(required = false) CreateMatchRequest ignored) {
    return matchService.createMatch();
  }

  @GetMapping("/{id}")
  public MatchState get(@PathVariable String id) {
    return matchService.getMatch(id);
  }

  @PostMapping("/{id}/moves")
  public MatchState move(@PathVariable String id, @RequestBody MoveRequest moveRequest) {
    return matchService.applyMove(id, moveRequest.index(), moveRequest.player());
  }
}
