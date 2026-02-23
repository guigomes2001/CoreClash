package manager;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.coreclash.MainActivity;
import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import enums.DomainLanguage;
import util.StyledToast;

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

        AlertDialog languageDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.btn_language))
                .setItems(options, (dialog, which) -> {
                    DomainLanguage selected = DomainLanguage.values()[which];
                    setAppLocale(selected.getTag());
                })
                .setNegativeButton(R.string.btn_close, null)
                .create();
        languageDialog.show();
        applyDialogStyle(languageDialog);
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
        applyDialogStyle(dialog);
    }

    private void applyDialogStyle(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_cyber_glass_v2);
        }

        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(Color.parseColor("#EAF6FF"));
            message.setTextSize(15f);
        }

        int titleId = activity.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(Color.parseColor("#D8EEFF"));
        }

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        if (positive != null) {
            positive.setAllCaps(false);
            positive.setTextColor(Color.parseColor("#6EE7FF"));
        }
        if (negative != null) {
            negative.setAllCaps(false);
            negative.setTextColor(Color.parseColor("#D8E9FF"));
        }
    }

    private void openPrivacyPolicy() {
        String rawUrl = activity.getString(R.string.privacy_policy_url).trim();
        if (rawUrl.isEmpty()) {
            StyledToast.show(activity, activity.getString(R.string.privacy_link_invalid));
            return;
        }

        String safeUrl = rawUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? rawUrl : "https://" + rawUrl;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            StyledToast.show(activity, activity.getString(R.string.privacy_browser_not_found));
        } catch (Exception e) {
            StyledToast.show(activity, activity.getString(R.string.privacy_link_invalid));
        }
    }

    public void setAppLocale(String languageTag) {
        LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocales);
    }
}