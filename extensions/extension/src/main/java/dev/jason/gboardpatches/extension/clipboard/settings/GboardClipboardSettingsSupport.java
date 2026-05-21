package dev.jason.gboardpatches.extension.clipboard;

import android.content.SharedPreferences;

final class GboardClipboardSettingsSupport {
    private GboardClipboardSettingsSupport() {
    }

    static String readSelectionValue(SharedPreferences preferences, String key,
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

    static String formatCustomMinutesLabel(int minutes) {
        return "Custom (" + minutes + (minutes == 1 ? " minute" : " minutes") + ")";
    }

    static String formatCustomCountLabel(int count) {
        return "Custom (" + count + ")";
    }
}
