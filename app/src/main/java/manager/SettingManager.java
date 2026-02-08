package manager;

import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.coreclash.MainActivity;
import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import enums.Language;

public class SettingManager {

    private final MainActivity activity;
    private final ActivityMainBinding binding;

    public SettingManager(MainActivity activity, ActivityMainBinding binding) {
        this.activity = activity;
        this.binding = binding;
        setupActions();
    }

    private void setupActions() {
        binding.btnSettingsClose.setOnClickListener(v -> closeSettings());
        binding.settingsOverlay.setOnClickListener(v -> closeSettings());
        binding.btnChangeLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    public void openSettings() {
        binding.settingsOverlay.setVisibility(View.VISIBLE);
        binding.settingsOverlay.setAlpha(0f);
        binding.settingsCard.setScaleX(0.85f);
        binding.settingsCard.setScaleY(0.85f);

        binding.settingsOverlay.animate().alpha(1f).setDuration(200).start();
        binding.settingsCard.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .setDuration(300).start();
    }

    public void closeSettings() {
        binding.settingsCard.animate().scaleX(0.9f).scaleY(0.9f).setDuration(150).start();
        binding.settingsOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> binding.settingsOverlay.setVisibility(View.GONE))
                .start();
    }

    private void showLanguageDialog() {
        String[] options = Language.getDisplayNames();

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.btn_language))
                .setItems(options, (dialog, which) -> {

                    Language selected = Language.values()[which];
                    setAppLocale(selected.getTag());

                })
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    public void setAppLocale(String languageTag) {
        LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocales);
    }
}