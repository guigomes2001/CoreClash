package com.example.coreclash.battlepass.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coreclash.R;
import com.example.coreclash.battlepass.data.BattlePassRepository;
import com.example.coreclash.battlepass.data.MissionsRepository;
import com.example.coreclash.battlepass.model.BpState;
import com.example.coreclash.battlepass.model.Mission;
import com.example.coreclash.battlepass.model.Season;
import com.example.coreclash.databinding.ActivityMissionsBinding;

import java.util.List;
import util.StyledToast;

public class MissionsActivity extends AppCompatActivity {

    private ActivityMissionsBinding binding;
    private final MissionsRepository missionsRepository = new MissionsRepository();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBackMissions.setOnClickListener(v -> finish());

        BattlePassRepository repository = new BattlePassRepository(this);
        repository.fetchActiveSeasonAndState(new BattlePassRepository.SeasonStateCallback() {
            @Override
            public void onResult(Season season, BpState state) {
                loadMissions(season, state);
            }

            @Override
            public void onError(Exception error) {
                StyledToast.show(MissionsActivity.this, getString(R.string.bp_error_loading));
            }
        });
    }

    private void loadMissions(Season season, BpState state) {
        missionsRepository.fetchMissions(season.id, missions -> renderMissions(missions, state),
                error -> StyledToast.show(this, getString(R.string.bp_error_loading)));
    }

    private void renderMissions(List<Mission> missions, BpState state) {
        StringBuilder builder = new StringBuilder();
        for (Mission mission : missions) {
            int progress = state.missionProgress.getOrDefault(mission.id, 0);
            builder.append(getString(R.string.bp_mission_row, mission.bucket, mission.metric, progress, mission.goal, mission.xpReward)).append("\n\n");
        }
        binding.txtMissionsList.setText(builder.toString().trim());
    }
}