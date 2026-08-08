package dev.jason.gboardpatches.extension.ocr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardOcrSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String VALUE_UNUSED = "__unused__";

    private final String entryTitle;
    private final String headerBadge;
    private final String entrySummary;
    private final String errorTitle;
    private final String errorSummary;
    private final String engineTitle;
    private final String engineDialogTitle;
    private final String sectionBehavior;
    private final String[] engineLabels;
    private final String[] engineValues;

    public GboardOcrSettingsFeature(Context context) {
        entryTitle = GboardSettingsText.get(context, R.string.gboard_patches_ocr_title);
        headerBadge = GboardSettingsText.get(context, R.string.gboard_patches_header_badge);
        entrySummary = GboardSettingsText.get(context, R.string.gboard_patches_ocr_summary);
        errorTitle = GboardSettingsText.get(context, R.string.gboard_patches_ocr_error_title);
        errorSummary = GboardSettingsText.get(context, R.string.gboard_patches_ocr_error_summary);
        engineTitle = GboardSettingsText.get(context, R.string.gboard_patches_ocr_engine_title);
        engineDialogTitle = GboardSettingsText.get(
                context, R.string.gboard_patches_ocr_engine_dialog_title);
        sectionBehavior = GboardSettingsText.get(
                context, R.string.gboard_patches_ocr_section_behavior);
        engineLabels = new String[]{
                GboardSettingsText.get(context, R.string.gboard_patches_ocr_engine_latin_label),
                GboardSettingsText.get(context, R.string.gboard_patches_ocr_engine_chinese_label),
                GboardSettingsText.get(context, R.string.gboard_patches_ocr_engine_japanese_label),
                GboardSettingsText.get(context, R.string.gboard_patches_ocr_engine_korean_label),
                GboardSettingsText.get(
                        context, R.string.gboard_patches_ocr_engine_devanagari_label)
        };
        engineValues = new String[]{
                GboardOcrEngine.LATIN.getSettingsValue(),
                GboardOcrEngine.CHINESE.getSettingsValue(),
                GboardOcrEngine.JAPANESE.getSettingsValue(),
                GboardOcrEngine.KOREAN.getSettingsValue(),
                GboardOcrEngine.DEVANAGARI.getSettingsValue()
        };
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_OCR_SCAN_TEXT);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardOcrSettings.preferences(context);
            GboardOcrSettings.ensureDefaults(preferences);
            String currentEngine = GboardOcrSettings.readEngineValue(preferences);

            List<GboardPatchesSettingsContract.Row> behaviorRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            behaviorRows.add(new GboardPatchesSettingsContract.SelectorRow(
                    engineTitle,
                    "",
                    labelForValue(currentEngine),
                    true,
                    () -> showEngineDialog(host, preferences, currentEngine)));

            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    "",
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            sectionBehavior,
                            behaviorRows)),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render OCR settings screen", failure);
            return buildErrorScreen();
        }
    }

    private void showEngineDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences, String currentEngine) {
        GboardPatchesSettingsContract.showChoiceDialog(
                host,
                engineDialogTitle,
                engineLabels,
                engineValues,
                currentEngine,
                VALUE_UNUSED,
                () -> {
                },
                value -> {
                    if (!GboardOcrSettings.writeEngine(preferences, value)) {
                        throw new IllegalStateException("Failed to save OCR engine");
                    }
                    GboardPatchesSettingsContract.refresh(host);
                });
    }

    private String labelForValue(String value) {
        for (int index = 0; index < engineValues.length; index++) {
            if (engineValues[index].equals(value)) {
                return engineLabels[index];
            }
        }
        return engineLabels[0];
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                errorTitle,
                errorSummary,
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                statusBlocks,
                Collections.emptyList());
    }
}
