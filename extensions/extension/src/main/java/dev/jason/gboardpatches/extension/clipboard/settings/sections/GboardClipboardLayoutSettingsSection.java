package dev.jason.gboardpatches.extension.clipboard;

import android.content.SharedPreferences;

import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

final class GboardClipboardLayoutSettingsSection {
    private static final String TITLE_COLUMN_COUNT = "Clipboard columns";
    private static final String SUMMARY_COLUMN_COUNT =
            "Controls how many columns the clipboard grid uses.";
    private static final String TITLE_PREVIEW_LINES = "Clipboard preview lines";
    private static final String SUMMARY_PREVIEW_LINES =
            "Controls how many preview lines each clipboard item can show before truncation.";
    private static final String LABEL_COLUMN_ONE = "1";
    private static final String LABEL_COLUMN_TWO_DEFAULT = "2 (Default)";
    private static final String LABEL_COLUMN_THREE = "3";
    private static final String LABEL_FIVE_DEFAULT = "5 (Default)";
    private static final String LABEL_TEN_PREVIEW = "10";
    private static final String LABEL_CUSTOM = "Custom";
    private static final String DIALOG_TITLE_CUSTOM_PREVIEW_LINES = "Custom preview lines";
    private static final String DIALOG_HINT_LINES = "Lines";
    private static final String DIALOG_TITLE_PREVIEW_IMAGE = "Clipboard preview lines";

    private static final GboardPatchesSettingsContract.PreviewSpec COLUMN_COUNT_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    TITLE_COLUMN_COUNT,
                    "",
                    GboardPatchesSettingsContract.PreviewLayout.STACKED,
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/column_count_one.png",
                            "1 column"),
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/column_count_three.png",
                            "3 columns"));
    private static final GboardPatchesSettingsContract.PreviewSpec PREVIEW_LINES_PREVIEW =
            new GboardPatchesSettingsContract.PreviewSpec(
                    DIALOG_TITLE_PREVIEW_IMAGE,
                    "",
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/preview_lines_default_5.png",
                            "Default (5 lines)"),
                    new GboardPatchesSettingsContract.PreviewImage(
                            "settings-previews/clipboard/preview_lines_extended_8.png",
                            "Extended (8 lines)"));

    void appendRows(List<GboardPatchesSettingsContract.Row> rows,
            GboardPatchesSettingsContract.Host host, SharedPreferences preferences,
            boolean clipboardEnabled) {
        rows.add(new GboardPatchesSettingsContract.ActionRow(
                TITLE_COLUMN_COUNT + ": " + currentColumnCountLabel(preferences),
                SUMMARY_COLUMN_COUNT,
                clipboardEnabled,
                () -> showColumnCountDialog(host, preferences),
                COLUMN_COUNT_PREVIEW));
        rows.add(new GboardPatchesSettingsContract.ActionRow(
                TITLE_PREVIEW_LINES + ": " + currentPreviewLinesLabel(preferences),
                SUMMARY_PREVIEW_LINES,
                clipboardEnabled,
                () -> showPreviewLinesDialog(host, preferences),
                PREVIEW_LINES_PREVIEW));
    }

    private void showColumnCountDialog(GboardPatchesSettingsContract.Host host,
            SharedPreferences preferences) {
        String[] labels = new String[] {
                LABEL_COLUMN_ONE,
                LABEL_COLUMN_TWO_DEFAULT,
                LABEL_COLUMN_THREE
        };
        String[] values = new String[] {
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_ONE),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_TWO),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_THREE)
        };
        host.showChoiceDialog(
                TITLE_COLUMN_COUNT,
                labels,
                values,
                Integer.toString(GboardClipboardSettings.readClipboardColumnCount(preferences)),
                "",
                () -> {
                },
                value -> preferences.edit()
                        .putString(GboardClipboardSettings.PREF_KEY_CLIPBOARD_COLUMN_COUNT, value)
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
                Integer.toString(GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED),
                GboardClipboardSettings.PREF_VALUE_CUSTOM
        };
        host.showChoiceDialog(
                TITLE_PREVIEW_LINES,
                labels,
                values,
                GboardClipboardSettingsSupport.readSelectionValue(
                        preferences,
                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                        Integer.toString(
                                GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES)),
                GboardClipboardSettings.PREF_VALUE_CUSTOM,
                () -> host.showPositiveIntegerDialog(
                        DIALOG_TITLE_CUSTOM_PREVIEW_LINES,
                        DIALOG_HINT_LINES,
                        GboardClipboardSettings.readClipboardContentMaxLinesCustomValue(
                                preferences),
                        value -> preferences.edit()
                                .putString(
                                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                        GboardClipboardSettings.PREF_VALUE_CUSTOM)
                                .putInt(
                                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                                        value)
                                .apply()),
                value -> preferences.edit()
                        .putString(GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                value)
                        .apply());
    }

    private String currentColumnCountLabel(SharedPreferences preferences) {
        int selection = GboardClipboardSettings.readClipboardColumnCount(preferences);
        if (selection == GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_ONE) {
            return LABEL_COLUMN_ONE;
        }
        if (selection == GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_THREE) {
            return LABEL_COLUMN_THREE;
        }
        return LABEL_COLUMN_TWO_DEFAULT;
    }

    private String currentPreviewLinesLabel(SharedPreferences preferences) {
        String selection = GboardClipboardSettingsSupport.readSelectionValue(
                preferences,
                GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        if (GboardClipboardSettings.PREF_VALUE_CUSTOM.equals(selection)) {
            return GboardClipboardSettingsSupport.formatCustomCountLabel(
                    GboardClipboardSettings.readClipboardContentMaxLinesCustomValue(preferences));
        }
        if (Integer.toString(GboardClipboardSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED)
                .equals(selection)) {
            return LABEL_TEN_PREVIEW;
        }
        return LABEL_FIVE_DEFAULT;
    }
}
