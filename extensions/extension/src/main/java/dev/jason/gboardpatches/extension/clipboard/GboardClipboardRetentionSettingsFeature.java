package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardClipboardRetentionSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardClipboard";
    private static final String HEADER_BADGE = "Gboard";
    private static final String ENTRY_TITLE = "Clipboard";
    private static final String ENTRY_SUMMARY =
            "Adjusts clipboard retention, item limits, preview lines, and metadata labels.";
    private static final String ERROR_TITLE = "Clipboard settings unavailable";
    private static final String ERROR_SUMMARY =
            "The clipboard settings screen failed to load. Reopen Gboard settings and try again.";
    private static final String TITLE_SHOW_EXPIRY_COUNTDOWN = "Show expiry countdown";
    private static final String SUMMARY_SHOW_EXPIRY_COUNTDOWN =
            "Shows the remaining retention time above each clipboard item.";
    private static final String TITLE_SHOW_CREATION_TIME = "Show creation time";
    private static final String SUMMARY_SHOW_CREATION_TIME =
            "Shows when each clipboard item was created in the current device time zone.";
    private static final String TITLE_PREVIEW_LINES = "Clipboard preview lines";
    private static final String SUMMARY_PREVIEW_LINES =
            "Controls how many preview lines each clipboard item can show before truncation.";
    private static final String TITLE_TTL = "Retention TTL";
    private static final String SUMMARY_TTL =
            "Controls how long unpinned clipboard items remain visible before they expire.";
    private static final String TITLE_MAX_COUNT = "Max count";
    private static final String SUMMARY_MAX_COUNT =
            "Limits how many unpinned clipboard items stay visible at the same time.";
    private static final String LABEL_CUSTOM = "Custom";
    private static final String LABEL_INFINITE = "Infinite";
    private static final String LABEL_ONE_MINUTE = "1 minute";
    private static final String LABEL_ONE_HOUR_DEFAULT = "1 hour (Default)";
    private static final String LABEL_TEN = "10";
    private static final String LABEL_HUNDRED_DEFAULT = "100 (Default)";
    private static final String LABEL_FIVE_DEFAULT = "5 (Default)";
    private static final String LABEL_TEN_PREVIEW = "10";
    private static final String DIALOG_TITLE_CUSTOM_TTL = "Custom retention TTL";
    private static final String DIALOG_HINT_MINUTES = "Minutes";
    private static final String DIALOG_TITLE_CUSTOM_MAX_COUNT = "Custom max count";
    private static final String DIALOG_HINT_COUNT = "Count";
    private static final String DIALOG_TITLE_CUSTOM_PREVIEW_LINES = "Custom preview lines";
    private static final String DIALOG_HINT_LINES = "Lines";
    private static final String DIALOG_TITLE_PREVIEW_IMAGE = "Clipboard preview lines";
    private static final String DIALOG_MESSAGE_PREVIEW_IMAGE =
            "";
    private static final GboardPatchesSettingsContract.PreviewSpec PREVIEW_LINES_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    DIALOG_TITLE_PREVIEW_IMAGE,
                    DIALOG_MESSAGE_PREVIEW_IMAGE,
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/preview_lines_default_5.png",
                            "Default (5 lines)"),
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/preview_lines_extended_8.png",
                            "Extended (8 lines)"));
    private static final GboardPatchesSettingsContract.PreviewSpec SHOW_EXPIRY_COUNTDOWN_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    TITLE_SHOW_EXPIRY_COUNTDOWN,
                    "",
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/show_expiry_countdown.png",
                            "Countdown example"));
    private static final GboardPatchesSettingsContract.PreviewSpec SHOW_CREATION_TIME_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    TITLE_SHOW_CREATION_TIME,
                    "",
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/show_creation_time.png",
                            "Creation time example"));

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
                GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_RETENTION);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        try {
            Context context = host.getContext();
            GboardClipboardRetentionRuntime.registerApplicationContext(
                    context.getApplicationContext());
            SharedPreferences preferences = GboardClipboardRetentionSettings.preferences(context);
            GboardClipboardRetentionSettings.ensureDefaults(preferences);

            boolean clipboardEnabled =
                    GboardClipboardRetentionSettings.readClipboardEnabled(preferences);
            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    ENTRY_TITLE,
                    ENTRY_SUMMARY,
                    true,
                    clipboardEnabled,
                    value -> preferences.edit()
                            .putBoolean(GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_ENABLED,
                                    value)
                            .apply()));
            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    TITLE_SHOW_EXPIRY_COUNTDOWN,
                    SUMMARY_SHOW_EXPIRY_COUNTDOWN,
                    clipboardEnabled,
                    GboardClipboardRetentionSettings.readClipboardShowCountdown(preferences),
                    value -> preferences.edit()
                            .putBoolean(
                                    GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN,
                                    value)
                            .apply(),
                    SHOW_EXPIRY_COUNTDOWN_PREVIEW));
            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    TITLE_SHOW_CREATION_TIME,
                    SUMMARY_SHOW_CREATION_TIME,
                    clipboardEnabled,
                    GboardClipboardRetentionSettings.readClipboardShowCreationTime(preferences),
                    value -> preferences.edit()
                            .putBoolean(
                                    GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_SHOW_CREATION_TIME,
                                    value)
                            .apply(),
                    SHOW_CREATION_TIME_PREVIEW));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_PREVIEW_LINES + ": " + currentPreviewLinesLabel(preferences),
                    SUMMARY_PREVIEW_LINES,
                    clipboardEnabled,
                    () -> showPreviewLinesDialog(host, preferences),
                    PREVIEW_LINES_PREVIEW));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_TTL + ": " + currentTtlLabel(preferences),
                    SUMMARY_TTL,
                    clipboardEnabled,
                    () -> showTtlDialog(host, preferences)));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    TITLE_MAX_COUNT + ": " + currentMaxCountLabel(preferences),
                    SUMMARY_MAX_COUNT,
                    clipboardEnabled,
                    () -> showMaxCountDialog(host, preferences)));
            return new GboardPatchesSettingsContract.Screen(
                    ENTRY_TITLE,
                    HEADER_BADGE,
                    ENTRY_TITLE,
                    ENTRY_SUMMARY,
                    rows);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render clipboard settings screen", throwable);
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
                ENTRY_SUMMARY,
                rows);
    }

    private void showTtlDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        String[] labels = new String[] {
                LABEL_ONE_MINUTE,
                LABEL_ONE_HOUR_DEFAULT,
                LABEL_INFINITE,
                LABEL_CUSTOM
        };
        String[] values = new String[] {
                Long.toString(GboardClipboardRetentionSettings.TTL_ONE_MINUTE_MS),
                Long.toString(GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_TTL_MS),
                Long.toString(GboardClipboardRetentionSettings.INFINITE_TTL_MS),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM
        };
        host.showChoiceDialog(
                TITLE_TTL,
                labels,
                values,
                readSelectionValue(
                        preferences,
                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_TTL_MS,
                        Long.toString(GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_TTL_MS)),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM,
                () -> host.showPositiveIntegerDialog(
                        DIALOG_TITLE_CUSTOM_TTL,
                        DIALOG_HINT_MINUTES,
                        GboardClipboardRetentionSettings.readClipboardTtlCustomMinutes(
                                preferences),
                        value -> preferences.edit()
                                .putString(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_TTL_MS,
                                        GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM)
                                .putInt(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES,
                                        value)
                                .apply()),
                value -> preferences.edit()
                        .putString(GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_TTL_MS,
                                value)
                        .apply());
    }

    private void showMaxCountDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        String[] labels = new String[] {
                LABEL_TEN,
                LABEL_HUNDRED_DEFAULT,
                LABEL_INFINITE,
                LABEL_CUSTOM
        };
        String[] values = new String[] {
                Integer.toString(GboardClipboardRetentionSettings.MAX_COUNT_TEN),
                Integer.toString(GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_MAX_COUNT),
                Integer.toString(GboardClipboardRetentionSettings.INFINITE_MAX_COUNT),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM
        };
        host.showChoiceDialog(
                TITLE_MAX_COUNT,
                labels,
                values,
                readSelectionValue(
                        preferences,
                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_MAX_COUNT,
                        Integer.toString(
                                GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_MAX_COUNT)),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM,
                () -> host.showPositiveIntegerDialog(
                        DIALOG_TITLE_CUSTOM_MAX_COUNT,
                        DIALOG_HINT_COUNT,
                        GboardClipboardRetentionSettings.readClipboardMaxCountCustomValue(
                                preferences),
                        value -> preferences.edit()
                                .putString(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_MAX_COUNT,
                                        GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM)
                                .putInt(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM,
                                        value)
                                .apply()),
                value -> preferences.edit()
                        .putString(GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_MAX_COUNT,
                                value)
                        .apply());
    }

    private void showPreviewLinesDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        String[] labels = new String[] {
                LABEL_FIVE_DEFAULT,
                LABEL_TEN_PREVIEW,
                LABEL_CUSTOM
        };
        String[] values = new String[] {
                Integer.toString(
                        GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES),
                Integer.toString(
                        GboardClipboardRetentionSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM
        };
        host.showChoiceDialog(
                TITLE_PREVIEW_LINES,
                labels,
                values,
                readSelectionValue(
                        preferences,
                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                        Integer.toString(
                                GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES)),
                GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM,
                () -> host.showPositiveIntegerDialog(
                        DIALOG_TITLE_CUSTOM_PREVIEW_LINES,
                        DIALOG_HINT_LINES,
                        GboardClipboardRetentionSettings.readClipboardContentMaxLinesCustomValue(
                                preferences),
                        value -> preferences.edit()
                                .putString(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                        GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM)
                                .putInt(
                                        GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                                        value)
                                .apply()),
                value -> preferences.edit()
                        .putString(
                                GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                value)
                        .apply());
    }

    private String currentTtlLabel(SharedPreferences preferences) {
        String selection = readSelectionValue(
                preferences,
                GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_TTL_MS,
                Long.toString(GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_TTL_MS));
        if (GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM.equals(selection)) {
            return formatCustomMinutesLabel(
                    GboardClipboardRetentionSettings.readClipboardTtlCustomMinutes(preferences));
        }
        if (Long.toString(GboardClipboardRetentionSettings.TTL_ONE_MINUTE_MS).equals(selection)) {
            return LABEL_ONE_MINUTE;
        }
        if (Long.toString(GboardClipboardRetentionSettings.INFINITE_TTL_MS).equals(selection)) {
            return LABEL_INFINITE;
        }
        return LABEL_ONE_HOUR_DEFAULT;
    }

    private String currentMaxCountLabel(SharedPreferences preferences) {
        String selection = readSelectionValue(
                preferences,
                GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_MAX_COUNT,
                Integer.toString(GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_MAX_COUNT));
        if (GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM.equals(selection)) {
            return formatCustomCountLabel(
                    GboardClipboardRetentionSettings.readClipboardMaxCountCustomValue(preferences));
        }
        if (Integer.toString(GboardClipboardRetentionSettings.MAX_COUNT_TEN).equals(selection)) {
            return LABEL_TEN;
        }
        if (Integer.toString(GboardClipboardRetentionSettings.INFINITE_MAX_COUNT).equals(selection)) {
            return LABEL_INFINITE;
        }
        return LABEL_HUNDRED_DEFAULT;
    }

    private String currentPreviewLinesLabel(SharedPreferences preferences) {
        String selection = readSelectionValue(
                preferences,
                GboardClipboardRetentionSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(
                        GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        if (GboardClipboardRetentionSettings.PREF_VALUE_CUSTOM.equals(selection)) {
            return formatCustomCountLabel(
                    GboardClipboardRetentionSettings.readClipboardContentMaxLinesCustomValue(
                            preferences));
        }
        if (Integer.toString(
                GboardClipboardRetentionSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED)
                .equals(selection)) {
            return LABEL_TEN_PREVIEW;
        }
        return LABEL_FIVE_DEFAULT;
    }

    private String readSelectionValue(SharedPreferences preferences, String key,
            String defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        return defaultValue;
    }

    private String formatCustomMinutesLabel(int minutes) {
        return LABEL_CUSTOM + " (" + minutes + (minutes == 1 ? " minute" : " minutes") + ")";
    }

    private String formatCustomCountLabel(int count) {
        return LABEL_CUSTOM + " (" + count + ")";
    }
}
