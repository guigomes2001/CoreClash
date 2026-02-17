package manager;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.coreclash.MainActivity;
import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import enums.DomainLanguage;

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
        binding.btnAboutPrivacy.setOnClickListener(v -> showPrivacyDialog());
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
        String[] options = DomainLanguage.getDisplayNames();

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.btn_language))
                .setItems(options, (dialog, which) -> {

                    DomainLanguage selected = DomainLanguage.values()[which];
                    setAppLocale(selected.getTag());

                })
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }


    private void showPrivacyDialog() {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.privacy_dialog_title))
                .setMessage(activity.getString(R.string.privacy_dialog_message))
                .setPositiveButton(R.string.privacy_dialog_open_policy, (dialog, which) -> openPrivacyPolicy())
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    private void openPrivacyPolicy() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.privacy_policy_url)));
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(intent);
        }
    }

    public void setAppLocale(String languageTag) {
        LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocales);
    }
}