package manager;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

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
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
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
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_privacy, null, false);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        Button btnClose = dialogView.findViewById(R.id.btnPrivacyClose);
        Button btnOpen = dialogView.findViewById(R.id.btnPrivacyOpen);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnOpen.setOnClickListener(v -> {
            openPrivacyPolicy();
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void openPrivacyPolicy() {
        String rawUrl = activity.getString(R.string.privacy_policy_url).trim();
        if (rawUrl.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.privacy_link_invalid), Toast.LENGTH_SHORT).show();
            return;
        }

        String safeUrl = rawUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? rawUrl : "https://" + rawUrl;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.privacy_browser_not_found), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.privacy_link_invalid), Toast.LENGTH_SHORT).show();
        }
    }

    public void setAppLocale(String languageTag) {
        LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocales);
    }
}
