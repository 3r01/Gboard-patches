package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardClipboardSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardClipboard";
    private static final String HEADER_BADGE = "Gboard";
    private static final String ENTRY_TITLE = "Clipboard";
    private static final String ENTRY_SUMMARY =
            "Adjusts clipboard retention, item limits, preview lines, metadata labels, order index, and columns.";
    private static final String ERROR_TITLE = "Clipboard settings unavailable";
    private static final String ERROR_SUMMARY =
            "The clipboard settings screen failed to load. Reopen Gboard settings and try again.";

    private final GboardClipboardMetadataSettingsSection metadataSection =
            new GboardClipboardMetadataSettingsSection();
    private final GboardClipboardLayoutSettingsSection layoutSection =
            new GboardClipboardLayoutSettingsSection();
    private final GboardClipboardRetentionSettingsSection retentionSection =
            new GboardClipboardRetentionSettingsSection();

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
                GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_ENHANCEMENTS);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        try {
            Context context = host.getContext();
            GboardClipboardRuntime.registerApplicationContext(context.getApplicationContext());
            SharedPreferences preferences = GboardClipboardSettings.preferences(context);
            GboardClipboardSettings.ensureDefaults(preferences);

            boolean clipboardEnabled = GboardClipboardSettings.readClipboardEnabled(preferences);
            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            rows.add(new GboardPatchesSettingsContract.SwitchRow(
                    ENTRY_TITLE,
                    ENTRY_SUMMARY,
                    true,
                    clipboardEnabled,
                    value -> preferences.edit()
                            .putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_ENABLED, value)
                            .apply()));
            metadataSection.appendRows(rows, host, preferences, clipboardEnabled);
            layoutSection.appendRows(rows, host, preferences, clipboardEnabled);
            retentionSection.appendRows(rows, host, preferences, clipboardEnabled);
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
}
