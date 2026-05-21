package dev.jason.gboardpatches.extension.symbolfooter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardSymbolFooterOrderSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String OFFICIAL_PANEL_NAME = "Emojis, stickers & GIFs";
    private static final String ENTRY_TITLE = OFFICIAL_PANEL_NAME + " Tab Order";
    private static final String HEADER_BADGE = "Gboard";
    private static final String ENTRY_SUMMARY =
            "Reorder the bottom tabs in Gboard's Emojis, stickers & GIFs panel.";
    private static final String HEADER_SUMMARY =
            "Controls the bottom tab order used by Gboard's Emojis, stickers & GIFs panel.";
    private static final String ERROR_TITLE = "Emojis, stickers & GIFs tab order unavailable";
    private static final String ERROR_SUMMARY =
            "The footer order screen failed to load. Reopen Gboard settings and try again.";
    private static final String CURRENT_ORDER_TITLE = "Current order";
    private static final String REORDER_TITLE = "Reorder tabs";
    private static final String REORDER_SUMMARY =
            "Drag the handle to move tabs.";
    private static final String RESET_TITLE = "Reset to default order";
    private static final String RESET_SUMMARY =
            "Restore the original bottom tab order for the tabs available in this build.";
    private static final String EMPTY_ORDER_SUMMARY = "No tabs available in this build.";

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
                GboardPatchesFeatureAvailability.FEATURE_SYMBOL_FOOTER_ORDER);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardSymbolFooterOrderSettings.preferences(context);
            GboardSymbolFooterOrderSettings.ensureDefaults(preferences);

            List<String> currentOrder = visibleOrder(
                    GboardSymbolFooterOrderSettings.readSymbolFooterOrder(preferences),
                    availableTabTypes(context));
            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    CURRENT_ORDER_TITLE,
                    buildCurrentOrderSummary(currentOrder),
                    true));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    REORDER_TITLE,
                    REORDER_SUMMARY,
                    true,
                    new ShowReorderDialogAction(this, host)));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    RESET_TITLE,
                    RESET_SUMMARY,
                    true,
                    false,
                    new ResetOrderAction(host)));
            return new GboardPatchesSettingsContract.Screen(
                    ENTRY_TITLE,
                    HEADER_BADGE,
                    ENTRY_TITLE,
                    HEADER_SUMMARY,
                    rows);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render Emojis, stickers & GIFs settings screen", throwable);
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
                HEADER_SUMMARY,
                rows);
    }

    private void showReorderDialog(GboardPatchesSettingsContract.Host host) {
        if (host == null || host.getContext() == null) {
            return;
        }
        try {
            Context context = host.getContext();
            if (!(context instanceof Activity)) {
                return;
            }
            Activity activity = (Activity) context;
            List<String> availableTabs = availableTabTypes(context);
            if (availableTabs.isEmpty()) {
                Log.i(TAG, "Skipping reorder dialog because no tabs are available");
                return;
            }
            List<String> storedOrder = GboardSymbolFooterOrderSettings.readSymbolFooterOrder(
                    context);
            List<String> currentOrder = visibleOrder(storedOrder, availableTabs);
            GboardSymbolFooterOrderEditorDialog.show(
                    activity,
                    currentOrder,
                    new TabLabelResolver(),
                    new SaveReorderedOrderAction(context, host, storedOrder, availableTabs));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show Emojis, stickers & GIFs reorder dialog", throwable);
        }
    }

    private static List<String> availableTabTypes(Context context) {
        List<String> tabs = new ArrayList<String>();
        boolean hasCustomSymbols = GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_CUSTOM_SYMBOLS);
        for (String tabType : GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER) {
            if (!hasCustomSymbols
                    && GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_CUSTOM_SYMBOLS.equals(
                            tabType)) {
                continue;
            }
            tabs.add(tabType);
        }
        return tabs;
    }

    private static List<String> visibleOrder(List<String> storedOrder, List<String> availableTabs) {
        LinkedHashSet<String> allowed = new LinkedHashSet<String>(availableTabs);
        LinkedHashSet<String> visible = new LinkedHashSet<String>();
        if (storedOrder != null) {
            for (String tabType : storedOrder) {
                if (allowed.contains(tabType)) {
                    visible.add(tabType);
                }
            }
        }
        for (String tabType : availableTabs) {
            visible.add(tabType);
        }
        return new ArrayList<String>(visible);
    }

    private static List<String> mergeStoredAndVisibleOrder(List<String> storedOrder,
            List<String> visibleOrder,
            List<String> availableTabs) {
        List<String> normalizedStoredOrder =
                GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER;
        if (storedOrder != null && !storedOrder.isEmpty()) {
            normalizedStoredOrder = new ArrayList<String>(storedOrder);
        }
        List<String> normalizedVisibleOrder = visibleOrder(visibleOrder, availableTabs);
        LinkedHashSet<String> availableSet = new LinkedHashSet<String>(availableTabs);
        Map<Integer, List<String>> hiddenTabsByVisibleSlot =
                new LinkedHashMap<Integer, List<String>>();
        int visibleCount = 0;
        for (String tabType : normalizedStoredOrder) {
            if (availableSet.contains(tabType)) {
                visibleCount++;
                continue;
            }
            List<String> tabsInSlot = hiddenTabsByVisibleSlot.get(Integer.valueOf(visibleCount));
            if (tabsInSlot == null) {
                tabsInSlot = new ArrayList<String>();
                hiddenTabsByVisibleSlot.put(Integer.valueOf(visibleCount), tabsInSlot);
            }
            tabsInSlot.add(tabType);
        }

        List<String> merged = new ArrayList<String>();
        appendHiddenTabsForSlot(hiddenTabsByVisibleSlot, merged, 0);
        for (int index = 0; index < normalizedVisibleOrder.size(); index++) {
            merged.add(normalizedVisibleOrder.get(index));
            appendHiddenTabsForSlot(hiddenTabsByVisibleSlot, merged, index + 1);
        }
        return merged;
    }

    private static void appendHiddenTabsForSlot(Map<Integer, List<String>> hiddenTabsByVisibleSlot,
            List<String> merged,
            int visibleSlot) {
        List<String> hiddenTabs = hiddenTabsByVisibleSlot.get(Integer.valueOf(visibleSlot));
        if (hiddenTabs == null || hiddenTabs.isEmpty()) {
            return;
        }
        merged.addAll(hiddenTabs);
    }

    private static String buildCurrentOrderSummary(List<String> currentOrder) {
        if (currentOrder == null || currentOrder.isEmpty()) {
            return EMPTY_ORDER_SUMMARY;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < currentOrder.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(index + 1)
                    .append(". ")
                    .append(tabLabel(currentOrder.get(index)));
        }
        return builder.toString();
    }

    private static String tabLabel(String tabType) {
        if (GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_EMOJI.equals(tabType)) {
            return "Emoji";
        }
        if (GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_CUSTOM_SYMBOLS.equals(tabType)) {
            return "Custom Symbols";
        }
        if (GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_EMOTICON.equals(tabType)) {
            return "Emoticon";
        }
        if (GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_GIF.equals(tabType)) {
            return "GIF";
        }
        if (GboardSymbolFooterOrderSettings.SYMBOL_FOOTER_TAB_STICKER.equals(tabType)) {
            return "Sticker";
        }
        return tabType;
    }

    private static final class ShowReorderDialogAction implements Runnable {
        private final GboardSymbolFooterOrderSettingsFeature feature;
        private final GboardPatchesSettingsContract.Host host;

        ShowReorderDialogAction(GboardSymbolFooterOrderSettingsFeature feature,
                GboardPatchesSettingsContract.Host host) {
            this.feature = feature;
            this.host = host;
        }

        @Override
        public void run() {
            if (feature == null || host == null) {
                return;
            }
            feature.showReorderDialog(host);
        }
    }

    private static final class ResetOrderAction implements Runnable {
        private final GboardPatchesSettingsContract.Host host;

        ResetOrderAction(GboardPatchesSettingsContract.Host host) {
            this.host = host;
        }

        @Override
        public void run() {
            if (host == null) {
                return;
            }
            try {
                GboardSymbolFooterOrderSettings.writeSymbolFooterOrder(
                        host.getContext(),
                        GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to reset expression footer tab order", throwable);
            }
            safeRefresh(host);
        }
    }

    private static final class TabLabelResolver
            implements GboardSymbolFooterOrderEditorDialog.LabelResolver {
        @Override
        public String labelFor(String tabType) {
            return tabLabel(tabType);
        }
    }

    private static final class SaveReorderedOrderAction
            implements GboardSymbolFooterOrderEditorDialog.SaveCallback {
        private final Context context;
        private final GboardPatchesSettingsContract.Host host;
        private final List<String> storedOrder;
        private final List<String> availableTabs;

        SaveReorderedOrderAction(Context context,
                GboardPatchesSettingsContract.Host host,
                List<String> storedOrder,
                List<String> availableTabs) {
            this.context = context;
            this.host = host;
            this.storedOrder = storedOrder;
            this.availableTabs = availableTabs;
        }

        @Override
        public void onSave(List<String> reorderedVisibleOrder) {
            try {
                List<String> mergedOrder =
                        mergeStoredAndVisibleOrder(storedOrder, reorderedVisibleOrder, availableTabs);
                Log.i(TAG, "Saving expression footer tab order: visible=" + reorderedVisibleOrder
                        + ", merged=" + mergedOrder
                        + ", stored=" + storedOrder
                        + ", available=" + availableTabs);
                GboardSymbolFooterOrderSettings.writeSymbolFooterOrder(
                        context,
                        mergedOrder);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to save expression footer tab order", throwable);
            } finally {
                safeRefresh(host);
            }
        }
    }

    private static void safeRefresh(GboardPatchesSettingsContract.Host host) {
        if (host == null) {
            return;
        }
        try {
            host.refresh();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to refresh settings after expression footer update", throwable);
        }
    }
}
