package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;

import java.util.Arrays;

import dev.jason.gboardpatches.extension.settings.GboardFeatureGroup;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.symbolfooter.GboardSymbolFooterOrderSettingsFeature;

public final class GboardKeyboardSettingsGroupFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String HEADER_BADGE = "Gboard";
    private static final String ENTRY_TITLE = "Keyboard";
    private static final String ENTRY_SUMMARY =
            "Keyboard-related patch settings.";
    private static final String HEADER_SUMMARY =
            "Keyboard-level settings for the patches included in this build.";
    private static final String EMPTY_TITLE = "No keyboard settings available";
    private static final String EMPTY_SUMMARY =
            "This build does not include any keyboard settings features.";

    private final GboardFeatureGroup delegate = new GboardFeatureGroup(
            ENTRY_TITLE,
            ENTRY_SUMMARY,
            HEADER_BADGE,
            HEADER_SUMMARY,
            EMPTY_TITLE,
            EMPTY_SUMMARY,
            Arrays.<GboardPatchesSettingsContract.Feature>asList(
                    new GboardSymbolFooterOrderSettingsFeature()));

    @Override
    public String getEntryTitle() {
        return delegate.getEntryTitle();
    }

    @Override
    public String getEntrySummary() {
        return delegate.getEntrySummary();
    }

    @Override
    public boolean isAvailable(Context context) {
        return delegate.isAvailable(context);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        return delegate.buildScreen(host);
    }
}
