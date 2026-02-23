package com.example.coreclash.battlepass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coreclash.R;
import com.example.coreclash.battlepass.anim.BattlePassAnimator;
import com.example.coreclash.battlepass.data.BattlePassRepository;
import com.example.coreclash.battlepass.domain.BpProgressCalculator;
import com.example.coreclash.battlepass.model.BpState;
import com.example.coreclash.battlepass.model.Season;
import com.example.coreclash.billing.BillingManager;
import com.example.coreclash.databinding.ActivityBattlePassBinding;
import util.StyledToast;

public class BattlePassActivity extends AppCompatActivity {

    private ActivityBattlePassBinding binding;
    private BattlePassRepository repository;
    private final BpProgressCalculator calculator = new BpProgressCalculator();
    private final BillingManager billingManager = new BillingManager();

    private Season season;
    private BpState bpState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBattlePassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new BattlePassRepository(this);
        BattlePassAnimator.playScreenEnter(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMissions.setOnClickListener(v -> startActivity(new Intent(this, MissionsActivity.class)));
        binding.btnBuyPremium.setOnClickListener(v -> billingManager.launchPremiumBattlePassPurchase(this));
        binding.btnRestorePremium.setOnClickListener(v -> billingManager.restorePremiumPass(active -> {
            if (bpState == null) return;
            bpState.premiumOwned = active;
            pushState();
            render();
        }));

        billingManager.start(this, new BillingManager.PurchaseListener() {
            @Override
            public void onCoinsGranted(int amount) {
            }

            @Override
            public void onRankedPassGranted() {
            }

            @Override
            public void onPremiumEntitlementChanged(boolean premiumOwned) {
                if (bpState == null) return;
                bpState.premiumOwned = premiumOwned;
                pushState();
                render();
            }
        });

        loadBattlePass();
    }

    private void loadBattlePass() {
        repository.fetchActiveSeasonAndState(new BattlePassRepository.SeasonStateCallback() {
            @Override
            public void onResult(Season loadedSeason, BpState loadedState) {
                season = loadedSeason;
                bpState = loadedState;
                repository.flushOfflineQueue();
                render();
            }

            @Override
            public void onError(Exception error) {
                StyledToast.show(BattlePassActivity.this, getString(R.string.bp_error_loading));
            }
        });
    }

    private void render() {
        if (season == null || bpState == null) return;
        int level = calculator.levelFromXp(season, bpState.xp);
        if (level > bpState.level) {
            bpState.level = level;
            BattlePassAnimator.playLevelUp(binding.txtLevelValue);
            pushState();
        }
        int xpCurrentLevel = calculator.xpIntoLevel(season, bpState.xp);
        int xpRequired = calculator.totalXpForLevel(season, level);
        binding.txtSeasonStatus.setText(getString(R.string.bp_season_status, season.status));
        binding.txtLevelValue.setText(getString(R.string.bp_level_value, bpState.level));
        binding.txtXpValue.setText(getString(R.string.bp_xp_value, xpCurrentLevel, xpRequired));
        binding.progressXp.setMax(Math.max(1, xpRequired));
        binding.progressXp.setProgress(xpCurrentLevel);
        binding.txtPremiumStatus.setText(getString(bpState.premiumOwned ? R.string.bp_premium_owned : R.string.bp_premium_locked));
    }

    private void pushState() {
        repository.upsertState(bpState, new BattlePassRepository.CompletionCallback() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }
}