package dev.jason.gboardpatches.extension.clipboard;

import android.content.SharedPreferences;

import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

final class GboardClipboardMetadataSettingsSection {
    private static final String TITLE_SHOW_EXPIRY_COUNTDOWN = "Show expiry countdown";
    private static final String SUMMARY_SHOW_EXPIRY_COUNTDOWN =
            "Shows the remaining retention time above each clipboard item.";
    private static final String TITLE_SHOW_CREATION_TIME = "Show creation time";
    private static final String SUMMARY_SHOW_CREATION_TIME =
            "Shows when each clipboard item was created in the current device time zone.";
    private static final String TITLE_SHOW_ORDER_INDEX = "Show order index";
    private static final String SUMMARY_SHOW_ORDER_INDEX =
            "Shows [1], [2], and so on above each clipboard item.";
    private static final String TITLE_ORDER_INDEX_DIRECTION = "Order index direction";
    private static final String SUMMARY_ORDER_INDEX_DIRECTION =
            "Controls whether [1] marks the newest or oldest clipboard item.";
    private static final String LABEL_NEWEST_FIRST_DEFAULT = "Newest first";
    private static final String LABEL_OLDEST_FIRST = "Oldest first";

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
    private static final GboardPatchesSettingsContract.PreviewSpec SHOW_ORDER_INDEX_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    TITLE_SHOW_ORDER_INDEX,
                    "",
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/show_order_index.png",
                            "Order index example"));

    void appendRows(List<GboardPatchesSettingsContract.Row> rows,
            GboardPatchesSettingsContract.Host host, SharedPreferences preferences,
            boolean clipboardEnabled) {
        rows.add(new GboardPatchesSettingsContract.SwitchRow(
                TITLE_SHOW_EXPIRY_COUNTDOWN,
                SUMMARY_SHOW_EXPIRY_COUNTDOWN,
                clipboardEnabled,
                GboardClipboardSettings.readClipboardShowCountdown(preferences),
                value -> preferences.edit()
                        .putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN,
                                value)
                        .apply(),
                SHOW_EXPIRY_COUNTDOWN_PREVIEW));
        rows.add(new GboardPatchesSettingsContract.SwitchRow(
                TITLE_SHOW_CREATION_TIME,
                SUMMARY_SHOW_CREATION_TIME,
                clipboardEnabled,
                GboardClipboardSettings.readClipboardShowCreationTime(preferences),
                value -> preferences.edit()
                        .putBoolean(
                                GboardClipboardSettings.PREF_KEY_CLIPBOARD_SHOW_CREATION_TIME,
                                value)
                        .apply(),
                SHOW_CREATION_TIME_PREVIEW));

        boolean showOrderIndex = GboardClipboardSettings.readClipboardShowOrderIndex(preferences);
        rows.add(new GboardPatchesSettingsContract.SwitchRow(
                TITLE_SHOW_ORDER_INDEX,
                SUMMARY_SHOW_ORDER_INDEX,
                clipboardEnabled,
                showOrderIndex,
                value -> preferences.edit()
                        .putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_SHOW_ORDER_INDEX,
                                value)
                        .apply(),
                SHOW_ORDER_INDEX_PREVIEW));
        rows.add(new GboardPatchesSettingsContract.ActionRow(
                TITLE_ORDER_INDEX_DIRECTION + ": " + currentOrderIndexDirectionLabel(preferences),
                SUMMARY_ORDER_INDEX_DIRECTION,
                clipboardEnabled && showOrderIndex,
                () -> showOrderIndexDirectionDialog(host, preferences)));
    }

    private void showOrderIndexDirectionDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        String[] labels = new String[] {
                LABEL_NEWEST_FIRST_DEFAULT,
                LABEL_OLDEST_FIRST
        };
        String[] values = new String[] {
                GboardClipboardSettings.CLIPBOARD_ORDER_INDEX_MODE_NEWEST_FIRST,
                GboardClipboardSettings.CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST
        };
        host.showChoiceDialog(
                TITLE_ORDER_INDEX_DIRECTION,
                labels,
                values,
                GboardClipboardSettings.readClipboardOrderIndexMode(preferences),
                "",
                () -> {
                },
                value -> preferences.edit()
                        .putString(GboardClipboardSettings.PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE,
                                value)
                        .apply());
    }

    private String currentOrderIndexDirectionLabel(SharedPreferences preferences) {
        String selection = GboardClipboardSettings.readClipboardOrderIndexMode(preferences);
        if (GboardClipboardSettings.CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST.equals(selection)) {
            return LABEL_OLDEST_FIRST;
        }
        return LABEL_NEWEST_FIRST_DEFAULT;
    }
}
