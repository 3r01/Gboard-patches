package dev.jason.gboardpatches.extension.settingshomepage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardSettingsHomepageSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String HEADER_BADGE = "Gboard";
    private static final String ENTRY_TITLE = "Settings Style";
    private static final String ENTRY_SUMMARY =
            "Choose whether Gboard settings use the new or legacy settings style.";
    private static final String HEADER_SUMMARY =
            "Control whether Gboard settings use the new or legacy settings experience. "
                    + "Reopen Gboard settings after changing this option.";
    private static final String ERROR_TITLE = "Settings homepage unavailable";
    private static final String ERROR_SUMMARY =
            "The homepage selector failed to load. Reopen Gboard settings and try again.";
    private static final String TITLE_MODE = "Settings style";
    private static final String TITLE_CURRENT = "Current active style";
    private static final String VALUE_UNUSED = "__unused__";
    private static final String LABEL_AUTO = "Auto";
    private static final String LABEL_NEW = "New";
    private static final String LABEL_LEGACY = "Legacy";
    private static final GboardPatchesSettingsContract.PreviewSpec SETTINGS_STYLE_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    TITLE_MODE,
                    "",
                    GboardPatchesSettingsContract.PreviewLayout.STACKED,
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/settingshomepage/settings_style_new.jpg",
                            "New style"),
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/settingshomepage/settings_style_legacy.jpg",
                            "Legacy style"));

    @Override
    public String getEntryTitle() {
        return ENTRY_TITLE;
    }

    @Override
    public String getEntrySummary() {
        return ENTRY_SUMMARY;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_SETTINGS_HOMEPAGE);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        try {
            Context context = host.getContext();
            SharedPreferences preferences = GboardSettingsHomepageSettings.preferences(context);
            GboardSettingsHomepageSettings.ensureDefaults(preferences);

            String selectedMode = GboardSettingsHomepageSettings.readMode(preferences);
            String currentHomepageLabel = GboardSettingsHomepageSettings.currentHomepageLabel(
                    context);

            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_MODE + ": " + modeLabel(selectedMode),
                    modeSummary(selectedMode),
                    true,
                    () -> showModeDialog(host, preferences),
                    SETTINGS_STYLE_PREVIEW));
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    TITLE_CURRENT,
                    currentHomepageSummary(currentHomepageLabel),
                    true));

            return new GboardPatchesSettingsContract.Screen(
                    ENTRY_TITLE,
                    HEADER_BADGE,
                    ENTRY_TITLE,
                    HEADER_SUMMARY,
                    rows);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render settings homepage feature", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        rows.add(new GboardPatchesSettingsContract.InfoRow(
                ERROR_TITLE,
                ERROR_SUMMARY,
                false));
        return new GboardPatchesSettingsContract.Screen(
                ENTRY_TITLE,
                HEADER_BADGE,
                ENTRY_TITLE,
                HEADER_SUMMARY,
                rows);
    }

    private void showModeDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        host.showChoiceDialog(
                TITLE_MODE,
                new String[] { LABEL_AUTO, LABEL_NEW, LABEL_LEGACY },
                new String[] {
                        GboardSettingsHomepageSettings.MODE_AUTO,
                        GboardSettingsHomepageSettings.MODE_FORCE_NEW,
                        GboardSettingsHomepageSettings.MODE_FORCE_LEGACY
                },
                GboardSettingsHomepageSettings.readMode(preferences),
                VALUE_UNUSED,
                () -> {
                },
                value -> preferences.edit()
                        .putString(GboardSettingsHomepageSettings.PREF_KEY_MODE, value)
                        .apply());
    }

    private String modeLabel(String mode) {
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(mode)) {
            return LABEL_NEW;
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_LEGACY.equals(mode)) {
            return LABEL_LEGACY;
        }
        return LABEL_AUTO;
    }

    private String modeSummary(String mode) {
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(mode)) {
            return "Always use the new settings style. Reopen Gboard settings to apply.";
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_LEGACY.equals(mode)) {
            return "Always use the legacy settings style. Reopen Gboard settings to apply.";
        }
        return "Follow Gboard automatic selection. Reopen Gboard settings after switching"
                + " modes.";
    }

    private String currentHomepageSummary(String currentHomepageLabel) {
        return currentHomepageLabel + " settings style is active right now.";
    }
}
