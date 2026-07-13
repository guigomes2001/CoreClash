package com.example.coreclash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import economy.DailyBonus;

public class DailyBonusTest {

    @Test
    public void first_launch_grants_day_one_bonus() {
        DailyBonus.Grant grant = DailyBonus.evaluate(0, 0, 20_000);
        assertNotNull(grant);
        assertEquals(1, grant.streakDay());
        assertEquals(40, grant.coins());
    }

    @Test
    public void same_day_claim_is_rejected() {
        assertNull(DailyBonus.evaluate(20_000, 1, 20_000));
    }

    @Test
    public void clock_rollback_is_rejected() {
        assertNull(DailyBonus.evaluate(20_001, 1, 20_000));
    }

    @Test
    public void consecutive_day_extends_streak() {
        DailyBonus.Grant grant = DailyBonus.evaluate(20_000, 1, 20_001);
        assertNotNull(grant);
        assertEquals(2, grant.streakDay());
        assertEquals(55, grant.coins());
    }

    @Test
    public void missed_day_resets_streak() {
        DailyBonus.Grant grant = DailyBonus.evaluate(20_000, 5, 20_002);
        assertNotNull(grant);
        assertEquals(1, grant.streakDay());
        assertEquals(40, grant.coins());
    }

    @Test
    public void reward_table_is_capped_at_seventh_day() {
        DailyBonus.Grant grant = DailyBonus.evaluate(20_000, 9, 20_001);
        assertNotNull(grant);
        assertEquals(10, grant.streakDay());
        assertEquals(150, grant.coins());
    }

    @Test
    public void epoch_day_conversion_uses_utc_days() {
        assertEquals(0, DailyBonus.epochDayOf(86_399_999L));
        assertEquals(1, DailyBonus.epochDayOf(86_400_000L));
    }
}
