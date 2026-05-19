package dev.jason.gboardpatches.extension.settingshomepage;

import android.content.Context;
import android.os.Build;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

public final class GboardSettingsHomepageSettings {
    public static final String PREF_FILE = "gboard_settings_homepage";
    public static final String PREF_KEY_MODE = "pref_settings_homepage_mode";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_FORCE_NEW = "force_new";
    public static final String MODE_FORCE_LEGACY = "force_legacy";

    public static final String LABEL_NEW = "New";
    public static final String LABEL_LEGACY = "Legacy";

    private GboardSettingsHomepageSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        if (context == null) {
            return;
        }
        ensureDefaults(preferences(context));
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        String storedMode = preferences.getString(PREF_KEY_MODE, MODE_AUTO);
        String sanitizedMode = sanitizeMode(storedMode);
        if (!preferences.contains(PREF_KEY_MODE) || !sanitizedMode.equals(storedMode)) {
            preferences.edit().putString(PREF_KEY_MODE, sanitizedMode).apply();
        }
    }

    public static String readMode(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readMode(preferences);
    }

    public static String readMode(SharedPreferences preferences) {
        if (preferences == null) {
            return MODE_AUTO;
        }
        return sanitizeMode(preferences.getString(PREF_KEY_MODE, MODE_AUTO));
    }

    public static boolean isAutoMode(Context context) {
        return MODE_AUTO.equals(readMode(context));
    }

    public static String currentHomepageLabel(Context context) {
        return isCurrentHomepageNew(context) ? LABEL_NEW : LABEL_LEGACY;
    }

    public static boolean isCurrentHomepageNew(Context context) {
        String mode = readMode(context);
        if (MODE_FORCE_NEW.equals(mode)) {
            return true;
        }
        if (MODE_FORCE_LEGACY.equals(mode)) {
            return false;
        }
        return resolveAutoUseNew(context);
    }

    public static boolean shouldUseNewSettingsStyle(Context context) {
        String mode = readMode(context);
        if (MODE_FORCE_NEW.equals(mode)) {
            return true;
        }
        if (MODE_FORCE_LEGACY.equals(mode)) {
            return false;
        }
        return resolveAutoUseNew(context);
    }

    private static String sanitizeMode(String value) {
        if (MODE_FORCE_NEW.equals(value)) {
            return MODE_FORCE_NEW;
        }
        if (MODE_FORCE_LEGACY.equals(value)) {
            return MODE_FORCE_LEGACY;
        }
        return MODE_AUTO;
    }

    private static boolean resolveAutoUseNew(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        if (readExpressiveSystemProperty(context)) {
            return true;
        }
        return readExpressiveAconfigFlag(context);
    }

    private static boolean readExpressiveSystemProperty(Context context) {
        try {
            Class<?> systemPropertiesClass = Class.forName(
                    "android.os.SystemProperties",
                    false,
                    context.getClassLoader());
            Method method = systemPropertiesClass.getMethod(
                    "getBoolean",
                    String.class,
                    Boolean.TYPE);
            Object value = method.invoke(null, "is_expressive_design_enabled", false);
            return value instanceof Boolean booleanValue && booleanValue.booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean readExpressiveAconfigFlag(Context context) {
        try {
            ClassLoader classLoader = context != null ? context.getClassLoader() : null;
            Class<?> aconfigPackageClass = Class.forName(
                    "android.os.flagging.AconfigPackage",
                    false,
                    classLoader);
            Method loadMethod = aconfigPackageClass.getMethod("load", String.class);
            Object aconfigPackage = loadMethod.invoke(
                    null,
                    "com.android.settingslib.widget.theme.flags");
            Method getBooleanFlagValueMethod = aconfigPackageClass.getMethod(
                    "getBooleanFlagValue",
                    String.class,
                    Boolean.TYPE);
            Object value = getBooleanFlagValueMethod.invoke(
                    aconfigPackage,
                    "is_expressive_design_enabled",
                    false);
            return value instanceof Boolean booleanValue && booleanValue.booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
