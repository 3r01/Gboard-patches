package dev.jason.gboardpatches.extension.settingshomepage;

import android.content.Context;

public final class GboardSettingsHomepageRuntime {
    private GboardSettingsHomepageRuntime() {
    }

    public static boolean shouldUseNewSettingsStyle(Context context) {
        return GboardSettingsHomepageSettings.shouldUseNewSettingsStyle(context);
    }
}
