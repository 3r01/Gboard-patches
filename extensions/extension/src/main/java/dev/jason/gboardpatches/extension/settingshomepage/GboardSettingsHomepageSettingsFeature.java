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
    private static final String SAFETY_TITLE = "Compatibility safeguard";
    private static final String RECOVERY_TITLE = "Crash safeguard";
    private static final String TRIAL_TITLE = "Trial window";
    private static final String TITLE_MODE = "Settings style";
    private static final String TITLE_CURRENT = "Current active style";
    private static final String VALUE_UNUSED = "__unused__";
    private static final String LABEL_AUTO = "Auto";
    private static final String LABEL_NEW = "New";
    private static final String LABEL_LEGACY = "Legacy";
    private static final long TRIAL_SCREEN_REFRESH_INTERVAL_MS = 1000L;
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
            GboardSettingsHomepageSettings.expireForceNewTrialIfNeeded(preferences);

            String selectedMode = GboardSettingsHomepageSettings.readMode(preferences);
            boolean forceNewSupported = GboardSettingsHomepageSettings.isForceNewSupported(
                    context);
            boolean crashRecoveryActive =
                    GboardSettingsHomepageSettings.isForceNewCrashRecoveryActive(preferences);
            boolean trialExpired =
                    GboardSettingsHomepageSettings.isForceNewTrialExpired(preferences)
                            || GboardSettingsHomepageSettings
                                    .isForceNewTrialWindowExpired(preferences);
            boolean trialArmed =
                    GboardSettingsHomepageSettings.isForceNewTrialArmed(preferences)
                            && !trialExpired;
            long trialRemainingSeconds = GboardSettingsHomepageSettings
                    .readForceNewTrialRemainingSeconds(preferences);
            String currentHomepageLabel = GboardSettingsHomepageSettings.currentHomepageLabel(
                    context);

            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_MODE + ": " + modeLabel(selectedMode, forceNewSupported,
                            crashRecoveryActive, trialArmed, trialExpired),
                    modeSummary(selectedMode, forceNewSupported, crashRecoveryActive,
                            trialArmed, trialExpired),
                    true,
                    () -> showModeDialog(host, preferences),
                    SETTINGS_STYLE_PREVIEW));
            if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                    && !forceNewSupported) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        SAFETY_TITLE,
                        "This Android version does not expose the expected expressive runtime. "
                                + "New still remains selectable, but it runs under crash "
                                + "recovery protection.",
                        true));
            }
            if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                    && trialArmed) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        TRIAL_TITLE,
                        "Open Gboard settings within " + trialRemainingSeconds
                                + " seconds. "
                                + "If nothing launches in that window, it falls back to "
                                + "Legacy automatically.",
                        true));
            }
            if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                    && crashRecoveryActive) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        RECOVERY_TITLE,
                        "The previous New settings launch did not finish cleanly. Legacy stays "
                                + "active so you can reopen settings and retry safely.",
                        true));
            }
            if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                    && trialExpired) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        TRIAL_TITLE,
                        "The New launch window expired before Gboard opened its settings page. "
                                + "Legacy stays active until you choose New again.",
                        true));
            }
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    TITLE_CURRENT,
                    currentHomepageSummary(currentHomepageLabel, selectedMode,
                            forceNewSupported, crashRecoveryActive, trialArmed, trialExpired),
                    true));

            return new GboardPatchesSettingsContract.Screen(
                    ENTRY_TITLE,
                    HEADER_BADGE,
                    ENTRY_TITLE,
                    HEADER_SUMMARY,
                    rows,
                    trialArmed ? TRIAL_SCREEN_REFRESH_INTERVAL_MS : 0L);
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
                value -> GboardSettingsHomepageSettings.writeMode(host.getContext(), value));
    }

    private String modeLabel(String mode, boolean forceNewSupported,
            boolean crashRecoveryActive, boolean trialArmed, boolean trialExpired) {
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(mode)) {
            if (crashRecoveryActive) {
                return LABEL_NEW + " (recovered)";
            }
            if (trialExpired) {
                return LABEL_NEW + " (expired)";
            }
            if (trialArmed) {
                return LABEL_NEW + " (armed)";
            }
            return LABEL_NEW;
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_LEGACY.equals(mode)) {
            return LABEL_LEGACY;
        }
        return LABEL_AUTO;
    }

    private String modeSummary(String mode, boolean forceNewSupported,
            boolean crashRecoveryActive, boolean trialArmed, boolean trialExpired) {
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(mode)) {
            if (crashRecoveryActive) {
                return "The previous New launch crashed. Legacy stays active until you switch"
                        + " modes or choose New again to retry.";
            }
            if (trialExpired) {
                return "The New launch window expired. Choose New again, then open Gboard "
                        + "settings within 10 seconds to retry.";
            }
            if (trialArmed) {
                return "Open Gboard settings within 10 seconds. If the launch crashes or never"
                        + " starts, it falls back to Legacy automatically.";
            }
            if (!forceNewSupported) {
                return "New stays available on this Android version, but it runs with automatic"
                        + " fallback protection if the settings launch fails.";
            }
            return "Always use the new settings style. Reopen Gboard settings to apply.";
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_LEGACY.equals(mode)) {
            return "Always use the legacy settings style. Reopen Gboard settings to apply.";
        }
        return "Follow Gboard automatic selection. Reopen Gboard settings after switching"
                + " modes.";
    }

    private String currentHomepageSummary(String currentHomepageLabel, String selectedMode,
            boolean forceNewSupported, boolean crashRecoveryActive, boolean trialArmed,
            boolean trialExpired) {
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                && (crashRecoveryActive || trialExpired)) {
            return "Legacy settings style is active right now because New is being held back by"
                    + " the safety guard.";
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode) && trialArmed) {
            return "New settings style is queued for the next Gboard settings launch.";
        }
        if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(selectedMode)
                && !forceNewSupported) {
            return "New settings style is selected with crash recovery protection on this "
                    + "Android version.";
        }
        return currentHomepageLabel + " settings style is active right now.";
    }
}
