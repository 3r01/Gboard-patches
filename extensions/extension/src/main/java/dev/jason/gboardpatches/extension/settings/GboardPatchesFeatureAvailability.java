package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public final class GboardPatchesFeatureAvailability {
    public static final String FEATURE_CLIPBOARD_RETENTION =
            "dev.jason.gboardpatches.feature.clipboard_retention";
    public static final String FEATURE_CLIPBOARD_ENTITY_EXTRACTION =
            "dev.jason.gboardpatches.feature.clipboard_entity_extraction";
    public static final String FEATURE_CLIPBOARD_ITEM_EDIT =
            "dev.jason.gboardpatches.feature.clipboard_item_edit";
    public static final String FEATURE_GRAMMAR_CHECKER =
            "dev.jason.gboardpatches.feature.grammar_checker";
    public static final String FEATURE_INLINE_SUGGESTIONS =
            "dev.jason.gboardpatches.feature.inline_suggestions";
    public static final String FEATURE_KEY_SHAPE_SELECTION =
            "dev.jason.gboardpatches.feature.key_shape_selection";
    public static final String FEATURE_SETTINGS_HOMEPAGE =
            "dev.jason.gboardpatches.feature.settings_homepage";

    private static final String TAG = "GboardPatches";

    private GboardPatchesFeatureAvailability() {
    }

    public static boolean hasFeature(Context context, String featureKey) {
        if (context == null || featureKey == null || featureKey.isEmpty()) {
            return false;
        }

        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        try {
            PackageManager packageManager = lookupContext.getPackageManager();
            if (packageManager == null) {
                return false;
            }
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    lookupContext.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            return metaData != null && metaData.getBoolean(featureKey, false);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to resolve feature marker: " + featureKey, throwable);
            return false;
        }
    }
}
