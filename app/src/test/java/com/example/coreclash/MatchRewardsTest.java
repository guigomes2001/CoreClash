package com.example.coreclash;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import economy.MatchRewards;

public class MatchRewardsTest {

    @Test
    public void casual_win_pays_base_plus_speed_bonus() {
        // base 25 + speed (9 - 5 = 4) + no streak bonus on first win
        assertEquals(29, MatchRewards.coinsFor(MatchRewards.Outcome.WIN, false, 1, 5));
    }

    @Test
    public void win_streak_adds_bonus_per_chained_win() {
        // base 25 + no speed bonus + streak (3 - 1) * 5 = 10
        assertEquals(35, MatchRewards.coinsFor(MatchRewards.Outcome.WIN, false, 3, 9));
    }

    @Test
    public void win_streak_bonus_is_capped() {
        // base 25 + streak bonus capped at 25
        assertEquals(50, MatchRewards.coinsFor(MatchRewards.Outcome.WIN, false, 20, 9));
    }

    @Test
    public void ranked_matches_pay_fifty_percent_more() {
        int casual = MatchRewards.coinsFor(MatchRewards.Outcome.WIN, false, 1, 9);
        int ranked = MatchRewards.coinsFor(MatchRewards.Outcome.WIN, true, 1, 9);
        assertEquals((casual * 3) / 2, ranked);
    }

    @Test
    public void draw_and_loss_pay_small_consolation() {
        assertEquals(8, MatchRewards.coinsFor(MatchRewards.Outcome.DRAW, false, 0, 9));
        assertEquals(3, MatchRewards.coinsFor(MatchRewards.Outcome.LOSS, false, 0, 6));
    }
}
