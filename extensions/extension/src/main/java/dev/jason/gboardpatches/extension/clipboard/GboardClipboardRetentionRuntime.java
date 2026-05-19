package dev.jason.gboardpatches.extension.clipboard;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"unused", "unchecked"})
public final class GboardClipboardRetentionRuntime {
    public static final String SETTINGS_PREF_FILE = "gboard_clipboard_retention";

    private static final String TAG = "GboardClipboard";

    private static final String CLIPBOARD_LOADER_CALLABLE_CLASS = "eln";
    private static final String CLIPBOARD_PRUNE_CALLABLE_CLASS = "emr";
    private static final String CLIPBOARD_ADAPTER_CLASS = "emk";
    private static final String CLIPBOARD_VIEW_HOLDER_CLASS = "emi";

    private static final long MINUTE_MS = 60_000L;
    private static final long INFINITE_TTL_MS = -1L;
    private static final int INFINITE_TTL_MINUTES = -1;
    private static final int INFINITE_MAX_COUNT = -1;
    private static final int DEFAULT_TTL_MINUTES = 60;
    private static final int DEFAULT_MAX_COUNT = 100;
    private static final int DEFAULT_PREVIEW_LINES = 5;

    private static final long STOCK_CLIPBOARD_TTL_MS = 3_600_000L;
    private static final int STOCK_CLIPBOARD_MAX_COUNT = 100;
    private static final int STOCK_CLIPBOARD_PRUNE_TRIGGER = 120;
    private static final int STOCK_CLIPBOARD_GROUP_LIMIT = 5;
    private static final int STOCK_CLIPBOARD_CONTENT_MAX_LINES = 5;
    private static final int LAST_VISIBLE_TIMESTAMP_PREF_RES_ID = 0x7f140928;
    private static final String LOG_PREFIX = "[gboard-clipboard]";
    private static final String[] CURSOR_COUNT_PROJECTION =
            new String[] { "_id", "timestamp", "item_type", "uri" };
    private static final String STOCK_SELECTION_RECENT_PINNED_SPECIAL =
            "((item_type & 1) = 0 AND (item_type & 2) = 0 AND timestamp >= ?)"
                    + " OR ((item_type & 1) != 0)"
                    + " OR ((item_type & 1) = 0 AND (item_type & 2) != 0)";
    private static final String SELECTION_TIMESTAMP_EQUALS = "timestamp = ?";
    private static final String SORT_TIMESTAMP_DESC = "timestamp DESC";
    private static final long COUNTDOWN_UPDATE_INTERVAL_MS = 1_000L;

    private static volatile Context APPLICATION_CONTEXT;
    private static final Map<ClassLoader, ReflectionHandles> HANDLES_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, ReflectionHandles>());
    private static final Map<TextView, CountdownBinding> ACTIVE_COUNTDOWN_BY_TEXT_VIEW =
            Collections.synchronizedMap(new WeakHashMap<TextView, CountdownBinding>());
    private static final Map<TextView, Integer> ACTIVE_MAX_LINES_OVERRIDE_BY_TEXT_VIEW =
            Collections.synchronizedMap(new WeakHashMap<TextView, Integer>());

    private static final AtomicInteger LOADER_INVOCATION_COUNT = new AtomicInteger(0);
    private static final AtomicInteger PRUNE_INVOCATION_COUNT = new AtomicInteger(0);
    private static final AtomicInteger TRIM_INVOCATION_COUNT = new AtomicInteger(0);
    private static final AtomicInteger COUNTDOWN_BIND_COUNT = new AtomicInteger(0);
    private static final AtomicInteger TEXT_MAX_LINES_PATCH_COUNT = new AtomicInteger(0);

    private GboardClipboardRetentionRuntime() {
    }

    public static boolean isClipboardRetentionEnabled() {
        Context context = applicationContext();
        return context == null || GboardClipboardRetentionSettings.readClipboardEnabled(context);
    }

    public static boolean shouldShowExpiryCountdown() {
        Context context = applicationContext();
        return context == null
                ? GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_SHOW_COUNTDOWN
                : GboardClipboardRetentionSettings.readClipboardShowCountdown(context);
    }

    public static boolean shouldShowCreationTime() {
        Context context = applicationContext();
        return context == null
                ? GboardClipboardRetentionSettings.DEFAULT_CLIPBOARD_SHOW_CREATION_TIME
                : GboardClipboardRetentionSettings.readClipboardShowCreationTime(context);
    }

    public static int configuredRetentionTtlMinutes() {
        Context context = applicationContext();
        if (context == null) {
            return DEFAULT_TTL_MINUTES;
        }
        long ttlMs = GboardClipboardRetentionSettings.readClipboardTtlMs(context);
        if (ttlMs == INFINITE_TTL_MS) {
            return INFINITE_TTL_MINUTES;
        }
        return sanitizeRetentionTtlMinutes((int) (ttlMs / MINUTE_MS));
    }

    public static int configuredMaxCount() {
        Context context = applicationContext();
        return context == null
                ? DEFAULT_MAX_COUNT
                : sanitizeMaxCount(GboardClipboardRetentionSettings.readClipboardMaxCount(context));
    }

    public static int configuredPreviewLines() {
        Context context = applicationContext();
        return context == null
                ? DEFAULT_PREVIEW_LINES
                : sanitizeContentMaxLines(
                        GboardClipboardRetentionSettings.readClipboardContentMaxLines(context));
    }

    public static boolean defaultShowExpiryCountdown() {
        return true;
    }

    public static boolean defaultShowCreationTime() {
        return false;
    }

    public static int defaultRetentionTtlMinutes() {
        return DEFAULT_TTL_MINUTES;
    }

    public static int defaultMaxCount() {
        return DEFAULT_MAX_COUNT;
    }

    public static int defaultPreviewLines() {
        return DEFAULT_PREVIEW_LINES;
    }

    public static void registerApplicationContext(Context context) {
        if (context == null) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext != null) {
            APPLICATION_CONTEXT = applicationContext;
        }
    }

    public static Object maybeBuildLoaderResult(Object receiver) {
        try {
            if (receiver == null) {
                return null;
            }
            registerContextFromReceiver(receiver);
            if (!isClipboardRetentionEnabled()) {
                return null;
            }
            return buildCustomLoaderResult(receiver);
        } catch (Throwable throwable) {
            logWarn("Failed to build custom clipboard loader result", throwable);
            return null;
        }
    }

    public static boolean handleCustomPrune(Object receiver) {
        try {
            if (receiver == null) {
                return false;
            }
            registerContextFromReceiver(receiver);
            if (!isClipboardRetentionEnabled()) {
                return false;
            }
            runCustomPrune(receiver);
            return true;
        } catch (Throwable throwable) {
            logWarn("Failed to run custom clipboard prune", throwable);
            return false;
        }
    }

    public static void afterAdapterTrim(Object receiver) {
        try {
            if (receiver == null) {
                return;
            }
            registerContextFromReceiver(receiver);
            if (!isClipboardRetentionEnabled()) {
                return;
            }
            enforceAdapterTrim(receiver);
        } catch (Throwable throwable) {
            logWarn("Failed to run clipboard adapter trim", throwable);
        }
    }

    public static void afterItemBind(Object receiver, Object holderObject, int position) {
        try {
            if (receiver == null) {
                return;
            }
            registerContextFromReceiver(receiver);
            bindClipboardMetadataLabel(receiver, holderObject, position);
        } catch (Throwable throwable) {
            logWarn("Failed to bind clipboard metadata label", throwable);
        }
    }

    private static ArrayList<Object> buildCustomLoaderResult(Object receiver) throws Throwable {
        ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
        Context context = loaderContext(handles, receiver);
        if (context == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        RuntimeSettings settings = runtimeSettings();
        long stockBaseCutoff = now - STOCK_CLIPBOARD_TTL_MS;
        long lastVisibleTimestamp = readLastVisibleTimestamp(handles, context);
        long stockEffectiveCutoff = Math.max(stockBaseCutoff, lastVisibleTimestamp);
        List<Object> stockQueryResult = queryClips(handles, context, stockEffectiveCutoff);
        ClipSections stockSections = splitClipSections(handles, stockQueryResult);
        long overrideEffectiveCutoff = effectiveVisibleCutoff(
                now, settings.clipboardTtlMs, lastVisibleTimestamp);
        List<Object> overrideQueryResult = queryClips(handles, context, overrideEffectiveCutoff);
        ClipSections overrideSections = splitClipSections(handles, overrideQueryResult);
        LoaderAssembly assembly = assembleVisibleSections(handles, context, overrideSections,
                settings);

        int stockPinnedVisibleLimit = computePinnedVisibleLimit(
                stockSections.pinned.size(),
                !stockSections.recent.isEmpty(),
                STOCK_CLIPBOARD_MAX_COUNT);
        int stockVisibleRecentLimit = computeVisibleRecentLimit(
                stockSections.recent.size(),
                stockPinnedVisibleLimit,
                STOCK_CLIPBOARD_MAX_COUNT);
        int stockRecentGroups = distinctTimestampCount(handles, stockSections.recent);

        int invocation = LOADER_INVOCATION_COUNT.incrementAndGet();
        logInfo(LOG_PREFIX + " loader#"
                + invocation
                + " stockTtlMs=" + STOCK_CLIPBOARD_TTL_MS
                + ", stockCutoff=" + stockEffectiveCutoff
                + ", stockLastVisibleTimestamp=" + lastVisibleTimestamp
                + ", stockQueryResultCount=" + stockQueryResult.size()
                + ", stockRecentCount=" + stockSections.recent.size()
                + ", stockPinnedCount=" + stockSections.pinned.size()
                + ", stockSpecialCount=" + stockSections.special.size()
                + ", stockVisibleMax=" + STOCK_CLIPBOARD_MAX_COUNT
                + ", stockRecentGroupLimit=" + STOCK_CLIPBOARD_GROUP_LIMIT
                + ", stockVisibleRecentLimit=" + stockVisibleRecentLimit
                + ", stockVisiblePinnedLimit=" + stockPinnedVisibleLimit
                + ", stockDistinctRecentGroups=" + stockRecentGroups
                + ", overrideTtlMs=" + settings.clipboardTtlMs
                + ", overrideCutoff=" + overrideEffectiveCutoff
                + ", overrideVisibleMax=" + settings.clipboardMaxCount
                + ", overrideGroupLimit=" + settings.clipboardGroupLimit
                + ", overrideRecentCount=" + assembly.visibleRecentCount
                + ", overridePinnedCount=" + assembly.visiblePinnedCount
                + ", overrideSpecialCount=" + assembly.visibleSpecialCount
                + ", primaryInserted=" + assembly.primaryInserted);
        logInfo(LOG_PREFIX + " loader#"
                + invocation
                + " stockRecentSample=" + describeClips(handles, stockSections.recent, 3)
                + ", stockPinnedSample=" + describeClips(handles, stockSections.pinned, 3)
                + ", overrideRecentSample=" + describeClips(handles, assembly.visibleRecent, 3));
        return new ArrayList<Object>(assembly.result);
    }

    private static void runCustomPrune(Object receiver) throws Throwable {
        ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
        Object dataHandler = handles.pruneCallableOwnerField.get(receiver);
        Context context = pruneContext(handles, receiver);
        if (context == null || dataHandler == null) {
            throw new IllegalStateException("Clipboard prune context is unavailable");
        }
        if (handles.dataHandlerDisabledField.getBoolean(dataHandler)) {
            return;
        }

        RuntimeSettings settings = runtimeSettings();
        Uri clipboardUri = createClipboardUri(handles, context);
        if (clipboardUri == null) {
            return;
        }

        int currentTotalCount = queryCursorCount(context, clipboardUri, null, null);
        int pinnedCount = queryItemTypeCount(handles, dataHandler, clipboardUri, 1);
        int unpinnedCount = queryItemTypeCount(handles, dataHandler, clipboardUri, 0);
        int specialCount = Math.max(0, currentTotalCount - pinnedCount - unpinnedCount);
        long primaryTimestamp = readPrimaryTimestamp(handles, context);
        long stockCutoff = System.currentTimeMillis() - STOCK_CLIPBOARD_TTL_MS;
        long overrideCutoff = settings.clipboardTtlMs < 0L
                ? -1L
                : System.currentTimeMillis() - settings.clipboardTtlMs;

        List<Long> expiredIds = new ArrayList<Long>();
        List<Long> overflowIds = new ArrayList<Long>();
        String unpinnedPreview = describeCursorRows(
                queryCursor(context, clipboardUri, CURSOR_COUNT_PROJECTION,
                        "item_type = ?",
                        new String[] { "0" },
                        SORT_TIMESTAMP_DESC),
                3);

        Cursor unpinnedCursor = null;
        Cursor remainingUnpinnedCursor = null;
        try {
            unpinnedCursor = queryItemTypeCursor(handles, dataHandler, clipboardUri, 0);
            if (settings.clipboardTtlMs >= 0L) {
                collectExpiredUnpinnedIds(handles, dataHandler, unpinnedCursor, overrideCutoff,
                        primaryTimestamp, expiredIds);
            }
            if (!expiredIds.isEmpty()) {
                deleteClipIds(handles, context, expiredIds);
            }
        } finally {
            closeQuietly(unpinnedCursor);
        }

        try {
            remainingUnpinnedCursor = queryItemTypeCursor(handles, dataHandler, clipboardUri, 0);
            int allowedUnpinnedRetainCount = computeRetainedUnpinnedCount(
                    settings.clipboardMaxCount, pinnedCount, safeCursorCount(remainingUnpinnedCursor));
            collectOverflowUnpinnedIds(handles, dataHandler, remainingUnpinnedCursor,
                    allowedUnpinnedRetainCount, primaryTimestamp, overflowIds);
            if (!overflowIds.isEmpty()) {
                deleteClipIds(handles, context, overflowIds);
            }
        } finally {
            closeQuietly(remainingUnpinnedCursor);
        }

        int totalCountAfter = queryCursorCount(context, clipboardUri, null, null);
        int unpinnedCountAfter = queryItemTypeCount(handles, dataHandler, clipboardUri, 0);
        Object countField = handles.dataHandlerCountField.get(dataHandler);
        if (countField instanceof AtomicInteger) {
            ((AtomicInteger) countField).set(totalCountAfter);
        }

        int invocation = PRUNE_INVOCATION_COUNT.incrementAndGet();
        logInfo(LOG_PREFIX + " prune#"
                + invocation
                + " stockTrigger=" + STOCK_CLIPBOARD_PRUNE_TRIGGER
                + ", stockRetainMax=" + STOCK_CLIPBOARD_MAX_COUNT
                + ", stockTtlMs=" + STOCK_CLIPBOARD_TTL_MS
                + ", stockCutoff=" + stockCutoff
                + ", overrideTtlMs=" + settings.clipboardTtlMs
                + ", overrideCutoff=" + overrideCutoff
                + ", overrideRetainMax=" + settings.clipboardMaxCount
                + ", dbTotalBefore=" + currentTotalCount
                + ", dbPinnedBefore=" + pinnedCount
                + ", dbUnpinnedBefore=" + unpinnedCount
                + ", dbSpecialBefore=" + specialCount
                + ", primaryTimestamp=" + primaryTimestamp
                + ", expiredCandidateCount=" + expiredIds.size()
                + ", overflowCandidateCount=" + overflowIds.size()
                + ", dbTotalAfter=" + totalCountAfter
                + ", dbUnpinnedAfter=" + unpinnedCountAfter);
        logInfo(LOG_PREFIX + " prune#"
                + invocation
                + " unpinnedBeforeSample=" + unpinnedPreview
                + ", expiredIds=" + describeIds(expiredIds, 5)
                + ", overflowIds=" + describeIds(overflowIds, 5));
    }

    private static void enforceAdapterTrim(Object receiver) throws Throwable {
        ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
        List<Object> items = adapterItems(handles, receiver);
        if (items == null || items.isEmpty()) {
            return;
        }
        Context context = adapterContext(handles, receiver);
        if (context == null) {
            return;
        }

        RuntimeSettings settings = runtimeSettings();
        int recentCount = handles.adapterRecentCountField.getInt(receiver);
        int pinnedVisibleCount = handles.adapterPinnedVisibleCountField.getInt(receiver);
        int stockGroupCount = distinctTimestampCountForAdapter(handles, items, recentCount);
        int allowedRecentCount = computeAdapterRecentLimit(
                handles,
                items,
                recentCount,
                settings.clipboardMaxCount,
                settings.clipboardGroupLimit);

        if (allowedRecentCount < recentCount) {
            for (int index = recentCount; index > allowedRecentCount; index--) {
                items.remove(index);
                handles.adapterNotifyItemRemovedMethod.invoke(receiver, index);
            }
            handles.adapterRecentCountField.setInt(receiver, allowedRecentCount);
            handles.adapterRefreshMethod.invoke(receiver);
        }

        int invocation = TRIM_INVOCATION_COUNT.incrementAndGet();
        logInfo(LOG_PREFIX + " trim#"
                + invocation
                + " stockVisibleMax=" + STOCK_CLIPBOARD_MAX_COUNT
                + ", stockGroupLimit=" + STOCK_CLIPBOARD_GROUP_LIMIT
                + ", stockPinnedVisibleCount=" + pinnedVisibleCount
                + ", stockRecentCount=" + recentCount
                + ", stockDistinctRecentGroups=" + stockGroupCount
                + ", overrideVisibleMax=" + settings.clipboardMaxCount
                + ", overrideGroupLimit=" + settings.clipboardGroupLimit
                + ", overrideRecentCount=" + handles.adapterRecentCountField.getInt(receiver)
                + ", recentSample=" + describeAdapterRecent(handles, items,
                        handles.adapterRecentCountField.getInt(receiver), 3));
    }

    private static void bindClipboardMetadataLabel(Object receiver, Object holderObject,
            int position) throws Throwable {
        ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
        if (holderObject == null || !handles.clipItemViewHolderClass.isInstance(holderObject)) {
            return;
        }

        Object labelViewObject = handles.clipItemViewHolderTextField.get(holderObject);
        if (!(labelViewObject instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) labelViewObject;
        clearCountdownBinding(textView);

        List<Object> items = adapterItems(handles, receiver);
        if (items == null || position < 0 || position >= items.size()) {
            return;
        }

        Object clip = items.get(position);
        if (clip == null || clip == handles.recentHeader || clip == handles.pinnedHeader
                || clip == handles.specialHeader) {
            return;
        }

        RuntimeSettings settings = runtimeSettings();
        if (!settings.enabled) {
            restoreClipboardTextMaxLines(textView);
            clearCountdownBinding(textView);
            return;
        }
        applyClipboardTextMaxLinesOverride(textView, position, settings);
        if (!settings.showExpiryCountdown && !settings.showCreationTime) {
            clearCountdownBinding(textView);
            return;
        }

        CharSequence currentText = textView.getText();
        String originalText = currentText == null ? "" : currentText.toString();
        CountdownBinding binding = new CountdownBinding(
                handles,
                textView,
                originalText,
                clipTimestamp(handles, clip),
                isPinned(handles, clip),
                isSpecial(handles, clip),
                settings.clipboardTtlMs);
        ACTIVE_COUNTDOWN_BY_TEXT_VIEW.put(textView, binding);
        updateClipboardMetadataLabel(binding);
        logLimited(COUNTDOWN_BIND_COUNT, 30,
                LOG_PREFIX + " bind position=" + position
                        + ", id=" + clipId(handles, clip)
                        + ", ts=" + binding.clipTimestamp
                        + ", pinned=" + binding.pinned
                        + ", special=" + binding.special
                        + ", label=" + formatMetadataLine(handles, binding, settings));
    }

    private static void updateClipboardMetadataLabel(CountdownBinding binding) throws Throwable {
        CountdownBinding activeBinding = ACTIVE_COUNTDOWN_BY_TEXT_VIEW.get(binding.textView);
        if (activeBinding != binding) {
            return;
        }

        RuntimeSettings settings = runtimeSettings();
        if (!settings.enabled) {
            restoreClipboardTextMaxLines(binding.textView);
            clearCountdownBinding(binding.textView);
            return;
        }
        applyClipboardTextMaxLinesOverride(binding.textView, -1, settings);
        if (!settings.showExpiryCountdown && !settings.showCreationTime) {
            clearCountdownBinding(binding.textView);
            return;
        }

        String metadataLine = formatMetadataLine(binding.handles, binding, settings);
        if (metadataLine == null || metadataLine.isEmpty()) {
            binding.textView.setText(binding.originalText);
        } else if (binding.originalText.isEmpty()) {
            binding.textView.setText(metadataLine);
        } else {
            binding.textView.setText(metadataLine + "\n" + binding.originalText);
        }

        binding.textView.removeCallbacks(binding);
        if (shouldContinueMetadataUpdates(binding, settings)) {
            binding.textView.postDelayed(binding, COUNTDOWN_UPDATE_INTERVAL_MS);
        }
    }

    private static boolean shouldContinueMetadataUpdates(CountdownBinding binding,
            RuntimeSettings settings) {
        if (!settings.showExpiryCountdown || binding.pinned || binding.special) {
            return false;
        }
        long remainingMs = remainingMs(binding.clipTimestamp, binding.clipboardTtlMs);
        if (remainingMs <= 0L) {
            return false;
        }
        return ACTIVE_COUNTDOWN_BY_TEXT_VIEW.get(binding.textView) == binding;
    }

    private static String formatMetadataLine(ReflectionHandles handles, CountdownBinding binding,
            RuntimeSettings settings) throws Throwable {
        List<String> tokens = new ArrayList<String>(2);
        if (settings.showExpiryCountdown) {
            tokens.add(formatCountdownToken(handles, binding));
        }
        if (settings.showCreationTime) {
            tokens.add(formatCreationTimeToken(binding.clipTimestamp));
        }
        if (tokens.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append('[').append(tokens.get(i)).append(']');
        }
        return builder.toString();
    }

    private static String formatCountdownToken(ReflectionHandles handles, CountdownBinding binding)
            throws Throwable {
        if (binding.pinned) {
            return "pinned";
        }
        if (binding.special) {
            return "special";
        }
        if (binding.clipboardTtlMs < 0L) {
            return "inf";
        }

        long remainingMs = remainingMs(binding.clipTimestamp, binding.clipboardTtlMs);
        long primaryTimestamp = readPrimaryTimestamp(handles, binding.textView.getContext());
        boolean isPrimary = primaryTimestamp == binding.clipTimestamp && primaryTimestamp > 0L;
        long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);
        String countdownText = formatRemainingDuration(remainingSeconds);
        return isPrimary ? countdownText + " primary" : countdownText;
    }

    private static String formatCreationTimeToken(long clipTimestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("M/d HH:mm", Locale.US);
        return formatter.format(new Date(clipTimestamp));
    }

    private static long remainingMs(long clipTimestamp, long ttlMs) {
        if (ttlMs < 0L) {
            return Long.MAX_VALUE;
        }
        return (clipTimestamp + ttlMs) - System.currentTimeMillis();
    }

    private static long effectiveVisibleCutoff(long now, long ttlMs, long lastVisibleTimestamp) {
        long baseCutoff = ttlMs < 0L ? 0L : now - ttlMs;
        return Math.max(baseCutoff, lastVisibleTimestamp);
    }

    private static String stripMetadataPrefix(CharSequence currentText) {
        if (currentText == null) {
            return "";
        }
        String text = currentText.toString();
        int start = 0;
        boolean consumedAny = false;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            String line = newline >= 0 ? text.substring(start, newline) : text.substring(start);
            if (!isMetadataLine(line)) {
                break;
            }
            consumedAny = true;
            if (newline < 0) {
                return "";
            }
            start = newline + 1;
        }
        if (!consumedAny) {
            return text;
        }
        return text.substring(start);
    }

    private static String formatRemainingDuration(long remainingSeconds) {
        long hours = remainingSeconds / 3600L;
        long minutes = (remainingSeconds % 3600L) / 60L;
        long seconds = remainingSeconds % 60L;

        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append('h');
            builder.append(minutes).append('m');
        } else if (minutes > 0L) {
            builder.append(minutes).append('m');
        }
        builder.append(seconds).append('s');
        return builder.toString();
    }

    private static boolean isMetadataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        int index = 0;
        while (index < line.length()) {
            if (line.charAt(index) != '[') {
                return false;
            }
            int end = line.indexOf(']', index);
            if (end < 0) {
                return false;
            }
            String token = line.substring(index + 1, end);
            if (!isMetadataToken(token)) {
                return false;
            }
            index = end + 1;
            if (index == line.length()) {
                return true;
            }
            if (line.charAt(index) != ' ') {
                return false;
            }
            index++;
        }
        return true;
    }

    private static boolean isMetadataToken(String token) {
        return isCountdownToken(token) || isCreationTimeToken(token);
    }

    private static boolean isCountdownToken(String token) {
        if ("pinned".equals(token) || "special".equals(token) || "inf".equals(token)) {
            return true;
        }
        return token.matches("(?:\\d+h\\d+m)?(?:\\d+m)?\\d+s(?: primary)?");
    }

    private static boolean isCreationTimeToken(String token) {
        return token.matches("\\d{1,2}/\\d{1,2} \\d{2}:\\d{2}");
    }

    private static void applyClipboardTextMaxLinesOverride(TextView textView, int position,
            RuntimeSettings settings) {
        int configuredMaxLines = settings.clipboardContentMaxLines;
        int before = textView.getMaxLines();
        Integer previousOverride = ACTIVE_MAX_LINES_OVERRIDE_BY_TEXT_VIEW.get(textView);
        if (before == configuredMaxLines) {
            ACTIVE_MAX_LINES_OVERRIDE_BY_TEXT_VIEW.put(textView, Integer.valueOf(configuredMaxLines));
            return;
        }
        if (before != STOCK_CLIPBOARD_CONTENT_MAX_LINES
                && (previousOverride == null || before != previousOverride.intValue())) {
            return;
        }

        textView.setMaxLines(configuredMaxLines);
        ACTIVE_MAX_LINES_OVERRIDE_BY_TEXT_VIEW.put(textView, Integer.valueOf(configuredMaxLines));
        int after = textView.getMaxLines();
        logLimited(TEXT_MAX_LINES_PATCH_COUNT, 20,
                LOG_PREFIX + " clipboard text maxLines override applied"
                        + " position=" + position
                        + ", configured=" + configuredMaxLines
                        + ", before=" + before
                        + ", after=" + after
                        + ", view=" + Integer.toHexString(System.identityHashCode(textView)));
    }

    private static void restoreClipboardTextMaxLines(TextView textView) {
        Integer previousOverride = ACTIVE_MAX_LINES_OVERRIDE_BY_TEXT_VIEW.remove(textView);
        if (previousOverride != null && textView.getMaxLines() == previousOverride.intValue()) {
            textView.setMaxLines(STOCK_CLIPBOARD_CONTENT_MAX_LINES);
        }
    }

    private static void clearCountdownBinding(TextView textView) {
        CountdownBinding previousBinding = ACTIVE_COUNTDOWN_BY_TEXT_VIEW.remove(textView);
        if (previousBinding != null) {
            textView.removeCallbacks(previousBinding);
            textView.setText(previousBinding.originalText);
            return;
        }

        CharSequence currentText = textView.getText();
        String strippedText = stripMetadataPrefix(currentText);
        if (currentText != null && !currentText.toString().contentEquals(strippedText)) {
            textView.setText(strippedText);
        }
    }

    private static LoaderAssembly assembleVisibleSections(ReflectionHandles handles, Context context,
            ClipSections sections, RuntimeSettings settings) throws Throwable {
        int pinnedVisibleLimit = computePinnedVisibleLimit(
                sections.pinned.size(),
                !sections.recent.isEmpty(),
                settings.clipboardMaxCount);
        int visibleRecentLimit = computeVisibleRecentLimit(
                sections.recent.size(),
                pinnedVisibleLimit,
                settings.clipboardMaxCount);
        List<Object> visibleRecent = new ArrayList<Object>(
                Math.min(sections.recent.size(), visibleRecentLimit + 1));
        for (int i = 0; i < sections.recent.size() && visibleRecent.size() < visibleRecentLimit; i++) {
            visibleRecent.add(sections.recent.get(i));
        }
        if (!visibleRecent.isEmpty()) {
            writeLastVisibleTimestamp(handles, context,
                    clipTimestamp(handles, visibleRecent.get(visibleRecent.size() - 1)));
        }

        boolean primaryInserted = false;
        long primaryTimestamp = readPrimaryTimestamp(handles, context);
        if (primaryTimestamp > 0L && !containsTimestamp(handles, visibleRecent, primaryTimestamp)) {
            List<Object> primaryMatches = queryByTimestamp(handles, context, primaryTimestamp);
            if (!primaryMatches.isEmpty()) {
                visibleRecent.add(primaryMatches.get(0));
                primaryInserted = true;
            }
        }

        ArrayList<Object> result = new ArrayList<Object>(
                3 + visibleRecent.size() + pinnedVisibleLimit + sections.special.size());
        result.add(handles.recentHeader);
        result.addAll(visibleRecent);
        result.add(handles.pinnedHeader);
        for (int i = 0; i < sections.pinned.size() && i < pinnedVisibleLimit; i++) {
            result.add(sections.pinned.get(i));
        }
        result.add(handles.specialHeader);
        result.addAll(sections.special);
        return new LoaderAssembly(result, visibleRecent,
                Math.min(sections.pinned.size(), pinnedVisibleLimit),
                sections.special.size(), primaryInserted);
    }

    private static List<Object> queryClips(ReflectionHandles handles, Context context, long cutoff)
            throws Throwable {
        String[] selectionArgs = new String[] { Long.toString(cutoff) };
        List<Object> result = (List<Object>) handles.queryClipsMethod.invoke(
                null,
                context,
                STOCK_SELECTION_RECENT_PINNED_SPECIAL,
                selectionArgs,
                SORT_TIMESTAMP_DESC);
        return result == null ? new ArrayList<Object>() : new ArrayList<Object>(result);
    }

    private static List<Object> queryByTimestamp(ReflectionHandles handles, Context context,
            long timestamp) throws Throwable {
        List<Object> result = (List<Object>) handles.queryClipsMethod.invoke(
                null,
                context,
                SELECTION_TIMESTAMP_EQUALS,
                new String[] { Long.toString(timestamp) },
                null);
        return result == null ? new ArrayList<Object>() : new ArrayList<Object>(result);
    }

    private static ClipSections splitClipSections(ReflectionHandles handles, List<Object> clips)
            throws Throwable {
        List<Object> recent = new ArrayList<Object>();
        List<Object> pinned = new ArrayList<Object>();
        List<Object> special = new ArrayList<Object>();
        for (Object clip : clips) {
            if (clip == null || clip == handles.recentHeader || clip == handles.pinnedHeader
                    || clip == handles.specialHeader) {
                continue;
            }
            if (isPinned(handles, clip)) {
                pinned.add(clip);
            } else if (isSpecial(handles, clip)) {
                special.add(clip);
            } else {
                recent.add(clip);
            }
        }
        return new ClipSections(recent, pinned, special);
    }

    private static int computePinnedVisibleLimit(int pinnedCount, boolean hasRecent,
            int visibleMaxCount) {
        if (visibleMaxCount < 0) {
            return pinnedCount;
        }
        if (pinnedCount >= visibleMaxCount) {
            return Math.max(0, visibleMaxCount - (hasRecent ? 1 : 0));
        }
        return pinnedCount;
    }

    private static int computeVisibleRecentLimit(int recentCount, int pinnedVisibleLimit,
            int visibleMaxCount) {
        if (visibleMaxCount < 0) {
            return recentCount;
        }
        return Math.min(recentCount, Math.max(0, visibleMaxCount - pinnedVisibleLimit));
    }

    private static int computeRetainedUnpinnedCount(int configuredMaxCount, int pinnedCount,
            int unpinnedCount) {
        if (configuredMaxCount < 0) {
            return unpinnedCount;
        }
        if (pinnedCount >= configuredMaxCount) {
            return unpinnedCount > 0 ? 1 : 0;
        }
        return Math.max(0, configuredMaxCount - pinnedCount);
    }

    private static int computeAdapterRecentLimit(ReflectionHandles handles, List<Object> items,
            int recentCount, int maxRecentCount, int maxGroupCount) throws Throwable {
        if (maxRecentCount < 0) {
            if (maxGroupCount == Integer.MAX_VALUE) {
                return recentCount;
            }
            maxRecentCount = recentCount;
        }
        if (recentCount <= maxRecentCount) {
            Set<Long> groups = new HashSet<Long>();
            for (int i = 1; i <= recentCount && i < items.size(); i++) {
                Object clip = items.get(i);
                groups.add(Long.valueOf(clipTimestamp(handles, clip)));
                if (groups.size() > maxGroupCount) {
                    return i - 1;
                }
            }
            return recentCount;
        }

        int allowedCount = maxRecentCount;
        Set<Long> groups = new HashSet<Long>();
        for (int i = 1; i < items.size() && i <= recentCount && allowedCount > 0; i++) {
            Object clip = items.get(i);
            groups.add(Long.valueOf(clipTimestamp(handles, clip)));
            if (groups.size() > maxGroupCount) {
                return i - 1;
            }
            allowedCount = i;
        }
        return Math.min(maxRecentCount, allowedCount);
    }

    private static int distinctTimestampCount(ReflectionHandles handles, List<Object> clips)
            throws Throwable {
        Set<Long> values = new HashSet<Long>();
        for (Object clip : clips) {
            values.add(Long.valueOf(clipTimestamp(handles, clip)));
        }
        return values.size();
    }

    private static int distinctTimestampCountForAdapter(ReflectionHandles handles, List<Object> items,
            int recentCount) throws Throwable {
        Set<Long> values = new HashSet<Long>();
        for (int i = 1; i <= recentCount && i < items.size(); i++) {
            values.add(Long.valueOf(clipTimestamp(handles, items.get(i))));
        }
        return values.size();
    }

    private static boolean containsTimestamp(ReflectionHandles handles, List<Object> clips,
            long timestamp) throws Throwable {
        for (Object clip : clips) {
            if (clipTimestamp(handles, clip) == timestamp) {
                return true;
            }
        }
        return false;
    }

    private static long readLastVisibleTimestamp(ReflectionHandles handles, Context context)
            throws Throwable {
        Object prefs = handles.preferencesAccessorMethod.invoke(null, context);
        Object value = handles.preferenceReadLongMethod.invoke(prefs,
                LAST_VISIBLE_TIMESTAMP_PREF_RES_ID,
                0L);
        return value instanceof Long ? ((Long) value).longValue() : 0L;
    }

    private static void writeLastVisibleTimestamp(ReflectionHandles handles, Context context,
            long value) throws Throwable {
        Object prefs = handles.preferencesAccessorMethod.invoke(null, context);
        handles.preferenceWriteLongMethod.invoke(prefs, LAST_VISIBLE_TIMESTAMP_PREF_RES_ID, value);
    }

    private static long readPrimaryTimestamp(ReflectionHandles handles, Context context)
            throws Throwable {
        Object value = handles.primaryTimestampReadMethod.invoke(null, context);
        return value instanceof Long ? ((Long) value).longValue() : 0L;
    }

    private static Uri createClipboardUri(ReflectionHandles handles, Context context)
            throws Throwable {
        Object value = handles.createClipboardUriMethod.invoke(null, context, 2, -1L);
        return value instanceof Uri ? (Uri) value : null;
    }

    private static Cursor queryItemTypeCursor(ReflectionHandles handles, Object dataHandler,
            Uri clipboardUri, int itemType) throws Throwable {
        Object value = handles.dataHandlerQueryByItemTypeMethod.invoke(
                dataHandler, clipboardUri, itemType);
        return value instanceof Cursor ? (Cursor) value : null;
    }

    private static int queryItemTypeCount(ReflectionHandles handles, Object dataHandler,
            Uri clipboardUri, int itemType) throws Throwable {
        Cursor cursor = null;
        try {
            cursor = queryItemTypeCursor(handles, dataHandler, clipboardUri, itemType);
            return safeCursorCount(cursor);
        } finally {
            closeQuietly(cursor);
        }
    }

    private static int queryCursorCount(Context context, Uri clipboardUri, String selection,
            String[] selectionArgs) {
        Cursor cursor = null;
        try {
            cursor = queryCursor(context, clipboardUri, CURSOR_COUNT_PROJECTION,
                    selection, selectionArgs, SORT_TIMESTAMP_DESC);
            return safeCursorCount(cursor);
        } finally {
            closeQuietly(cursor);
        }
    }

    private static void collectExpiredUnpinnedIds(ReflectionHandles handles, Object dataHandler,
            Cursor cursor, long overrideCutoff, long primaryTimestamp, List<Long> out)
            throws Throwable {
        if (cursor == null || cursor.isClosed() || cursor.getCount() == 0) {
            return;
        }
        int idColumn = cursor.getColumnIndexOrThrow("_id");
        int timestampColumn = cursor.getColumnIndexOrThrow("timestamp");
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            long timestamp = cursor.getLong(timestampColumn);
            if (timestamp < overrideCutoff && timestamp != primaryTimestamp) {
                out.add(Long.valueOf(cursor.getLong(idColumn)));
                handles.dataHandlerCleanupCursorMethod.invoke(dataHandler, cursor);
            }
            cursor.moveToNext();
        }
    }

    private static void collectOverflowUnpinnedIds(ReflectionHandles handles, Object dataHandler,
            Cursor cursor, int retainCount, long primaryTimestamp, List<Long> out)
            throws Throwable {
        if (cursor == null || cursor.isClosed() || cursor.getCount() == 0) {
            return;
        }
        int idColumn = cursor.getColumnIndexOrThrow("_id");
        int timestampColumn = cursor.getColumnIndexOrThrow("timestamp");
        cursor.moveToFirst();
        int kept = 0;
        while (!cursor.isAfterLast()) {
            long timestamp = cursor.getLong(timestampColumn);
            if (kept < retainCount || timestamp == primaryTimestamp) {
                kept++;
            } else {
                out.add(Long.valueOf(cursor.getLong(idColumn)));
                handles.dataHandlerCleanupCursorMethod.invoke(dataHandler, cursor);
            }
            cursor.moveToNext();
        }
    }

    private static void deleteClipIds(ReflectionHandles handles, Context context, List<Long> ids)
            throws Throwable {
        if (ids.isEmpty()) {
            return;
        }
        handles.deleteClipsMethod.invoke(null, context, ids);
    }

    private static String describeClips(ReflectionHandles handles, List<Object> clips, int limit)
            throws Throwable {
        if (clips.isEmpty()) {
            return "[]";
        }
        List<String> rows = new ArrayList<String>();
        for (int i = 0; i < clips.size() && i < limit; i++) {
            Object clip = clips.get(i);
            rows.add("{id=" + clipId(handles, clip)
                    + ", ts=" + clipTimestamp(handles, clip)
                    + ", pinned=" + isPinned(handles, clip)
                    + ", itemType=" + clipItemType(handles, clip)
                    + "}");
        }
        return rows.toString();
    }

    private static String describeAdapterRecent(ReflectionHandles handles, List<Object> items,
            int recentCount, int limit) throws Throwable {
        if (items.isEmpty() || recentCount <= 0) {
            return "[]";
        }
        List<String> rows = new ArrayList<String>();
        for (int i = 1; i <= recentCount && i < items.size() && rows.size() < limit; i++) {
            Object clip = items.get(i);
            rows.add("{id=" + clipId(handles, clip)
                    + ", ts=" + clipTimestamp(handles, clip)
                    + ", pinned=" + isPinned(handles, clip)
                    + ", itemType=" + clipItemType(handles, clip)
                    + "}");
        }
        return rows.toString();
    }

    private static String describeCursorRows(Cursor cursor, int limit) {
        if (cursor == null) {
            return "[]";
        }
        try {
            if (cursor.isClosed() || cursor.getCount() == 0) {
                return "[]";
            }
            int idColumn = cursor.getColumnIndexOrThrow("_id");
            int timestampColumn = cursor.getColumnIndexOrThrow("timestamp");
            int itemTypeColumn = cursor.getColumnIndexOrThrow("item_type");
            List<String> rows = new ArrayList<String>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast() && rows.size() < limit) {
                rows.add("{id=" + cursor.getLong(idColumn)
                        + ", ts=" + cursor.getLong(timestampColumn)
                        + ", itemType=" + cursor.getInt(itemTypeColumn)
                        + "}");
                cursor.moveToNext();
            }
            return rows.toString();
        } catch (Throwable throwable) {
            return "[<error:" + throwable.getClass().getSimpleName() + ">]";
        } finally {
            closeQuietly(cursor);
        }
    }

    private static String describeIds(List<Long> ids, int limit) {
        if (ids.isEmpty()) {
            return "[]";
        }
        List<Long> sample = ids.size() > limit ? ids.subList(0, limit) : ids;
        return sample.toString();
    }

    private static List<Object> adapterItems(ReflectionHandles handles, Object receiver)
            throws Throwable {
        return (List<Object>) handles.adapterItemsField.get(receiver);
    }

    private static long clipId(ReflectionHandles handles, Object clip) throws Throwable {
        return handles.clipIdField.getLong(clip);
    }

    private static long clipTimestamp(ReflectionHandles handles, Object clip) throws Throwable {
        return handles.clipTimestampField.getLong(clip);
    }

    private static int clipItemType(ReflectionHandles handles, Object clip) throws Throwable {
        Object model = handles.clipModelField.get(clip);
        return handles.clipModelItemTypeField.getInt(model);
    }

    private static boolean isPinned(ReflectionHandles handles, Object clip) throws Throwable {
        Object value = handles.clipIsPinnedMethod.invoke(clip);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isSpecial(ReflectionHandles handles, Object clip) throws Throwable {
        Object value = handles.clipIsSpecialMethod.invoke(clip);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static Cursor queryCursor(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        if (context == null || uri == null) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    private static int safeCursorCount(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) {
            return 0;
        }
        return cursor.getCount();
    }

    private static void closeQuietly(Cursor cursor) {
        if (cursor == null) {
            return;
        }
        try {
            cursor.close();
        } catch (RuntimeException ignored) {
            // Best effort.
        }
    }

    private static RuntimeSettings runtimeSettings() {
        Context context = applicationContext();
        if (context == null) {
            return RuntimeSettings.stockDefaults(true);
        }

        GboardClipboardRetentionSettings.ensureDefaults(context);
        boolean enabled = GboardClipboardRetentionSettings.readClipboardEnabled(context);
        if (!enabled) {
            return RuntimeSettings.stockDefaults(false);
        }

        return new RuntimeSettings(
                true,
                GboardClipboardRetentionSettings.readClipboardShowCountdown(context),
                GboardClipboardRetentionSettings.readClipboardShowCreationTime(context),
                GboardClipboardRetentionSettings.readClipboardTtlMs(context),
                sanitizeMaxCount(GboardClipboardRetentionSettings.readClipboardMaxCount(context)),
                sanitizeContentMaxLines(
                        GboardClipboardRetentionSettings.readClipboardContentMaxLines(context)));
    }

    private static void registerContextFromReceiver(Object receiver) throws Throwable {
        if (receiver == null) {
            return;
        }
        ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
        Context context = adapterContext(handles, receiver);
        if (context == null) {
            context = loaderContext(handles, receiver);
        }
        if (context == null) {
            context = pruneContext(handles, receiver);
        }
        registerApplicationContext(context);
    }

    private static Context applicationContext() {
        Context context = APPLICATION_CONTEXT;
        if (context != null) {
            return context;
        }

        context = reflectedApplicationContext("android.app.ActivityThread", "currentApplication");
        if (context != null) {
            APPLICATION_CONTEXT = context;
            return context;
        }

        context = reflectedApplicationContext("android.app.AppGlobals", "getInitialApplication");
        if (context != null) {
            APPLICATION_CONTEXT = context;
        }
        return context;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Class<?> owner = Class.forName(className);
            Method method = owner.getDeclaredMethod(methodName);
            Object application = method.invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int sanitizeRetentionTtlMinutes(int ttlMinutes) {
        if (ttlMinutes == INFINITE_TTL_MINUTES) {
            return ttlMinutes;
        }
        return ttlMinutes > 0 ? ttlMinutes : DEFAULT_TTL_MINUTES;
    }

    private static int sanitizeMaxCount(int maxCount) {
        if (maxCount == INFINITE_MAX_COUNT) {
            return maxCount;
        }
        return maxCount > 0 ? maxCount : DEFAULT_MAX_COUNT;
    }

    private static int sanitizeContentMaxLines(int value) {
        return value > 0 ? value : DEFAULT_PREVIEW_LINES;
    }

    private static Context loaderContext(ReflectionHandles handles, Object receiver)
            throws Throwable {
        if (receiver == null
                || !handles.loaderCallableOwnerField.getDeclaringClass().isInstance(receiver)) {
            return null;
        }
        Object loader = handles.loaderCallableOwnerField.get(receiver);
        if (loader == null) {
            return null;
        }
        if (!handles.loaderContextField.getDeclaringClass().isInstance(loader)) {
            return null;
        }
        return (Context) handles.loaderContextField.get(loader);
    }

    private static Context pruneContext(ReflectionHandles handles, Object receiver)
            throws Throwable {
        if (receiver == null
                || !handles.pruneCallableOwnerField.getDeclaringClass().isInstance(receiver)) {
            return null;
        }
        Object dataHandler = handles.pruneCallableOwnerField.get(receiver);
        if (dataHandler == null) {
            return null;
        }
        if (!handles.dataHandlerContextField.getDeclaringClass().isInstance(dataHandler)) {
            return null;
        }
        return (Context) handles.dataHandlerContextField.get(dataHandler);
    }

    private static Context adapterContext(ReflectionHandles handles, Object receiver)
            throws Throwable {
        if (receiver == null
                || !handles.adapterContextField.getDeclaringClass().isInstance(receiver)) {
            return null;
        }
        Object value = handles.adapterContextField.get(receiver);
        return value instanceof Context ? (Context) value : null;
    }

    private static ReflectionHandles reflectionHandles(ClassLoader classLoader) throws Throwable {
        synchronized (HANDLES_BY_LOADER) {
            ReflectionHandles cached = HANDLES_BY_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            ReflectionHandles created = new ReflectionHandles(classLoader);
            HANDLES_BY_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static void logInfo(String message) {
        Log.i(TAG, message);
    }

    private static void logWarn(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
    }

    private static void logLimited(AtomicInteger counter, int limit, String message) {
        int count = counter.incrementAndGet();
        if (count <= limit) {
            logInfo(message + " [count=" + count + "]");
        }
    }

    private static final class ClipSections {
        final List<Object> recent;
        final List<Object> pinned;
        final List<Object> special;

        ClipSections(List<Object> recent, List<Object> pinned, List<Object> special) {
            this.recent = recent;
            this.pinned = pinned;
            this.special = special;
        }
    }

    private static final class LoaderAssembly {
        final List<Object> result;
        final List<Object> visibleRecent;
        final int visibleRecentCount;
        final int visiblePinnedCount;
        final int visibleSpecialCount;
        final boolean primaryInserted;

        LoaderAssembly(List<Object> result, List<Object> visibleRecent, int visiblePinnedCount,
                int visibleSpecialCount, boolean primaryInserted) {
            this.result = result;
            this.visibleRecent = visibleRecent;
            this.visibleRecentCount = visibleRecent.size();
            this.visiblePinnedCount = visiblePinnedCount;
            this.visibleSpecialCount = visibleSpecialCount;
            this.primaryInserted = primaryInserted;
        }
    }

    private static final class RuntimeSettings {
        final boolean enabled;
        final boolean showExpiryCountdown;
        final boolean showCreationTime;
        final long clipboardTtlMs;
        final int clipboardMaxCount;
        final int clipboardContentMaxLines;
        final int clipboardGroupLimit;

        RuntimeSettings(boolean enabled, boolean showExpiryCountdown, boolean showCreationTime,
                long clipboardTtlMs, int clipboardMaxCount, int clipboardContentMaxLines) {
            this.enabled = enabled;
            this.showExpiryCountdown = showExpiryCountdown;
            this.showCreationTime = showCreationTime;
            this.clipboardTtlMs = clipboardTtlMs;
            this.clipboardMaxCount = clipboardMaxCount;
            this.clipboardContentMaxLines = clipboardContentMaxLines;
            this.clipboardGroupLimit = clipboardMaxCount < 0
                    ? Integer.MAX_VALUE
                    : Math.max(1, clipboardMaxCount);
        }

        static RuntimeSettings stockDefaults(boolean enabled) {
            return new RuntimeSettings(
                    enabled,
                    false,
                    false,
                    STOCK_CLIPBOARD_TTL_MS,
                    STOCK_CLIPBOARD_MAX_COUNT,
                    STOCK_CLIPBOARD_CONTENT_MAX_LINES);
        }
    }

    private static final class CountdownBinding implements Runnable {
        final ReflectionHandles handles;
        final TextView textView;
        final String originalText;
        final long clipTimestamp;
        final boolean pinned;
        final boolean special;
        final long clipboardTtlMs;

        CountdownBinding(ReflectionHandles handles, TextView textView, String originalText,
                long clipTimestamp, boolean pinned, boolean special, long clipboardTtlMs) {
            this.handles = handles;
            this.textView = textView;
            this.originalText = originalText == null ? "" : originalText;
            this.clipTimestamp = clipTimestamp;
            this.pinned = pinned;
            this.special = special;
            this.clipboardTtlMs = clipboardTtlMs;
        }

        @Override
        public void run() {
            try {
                updateClipboardMetadataLabel(this);
            } catch (Throwable throwable) {
                logWarn("Failed to update clipboard countdown label", throwable);
            }
        }
    }

    private static final class ReflectionHandles {
        final Field loaderCallableOwnerField;
        final Field loaderContextField;
        final Field pruneCallableOwnerField;
        final Field dataHandlerContextField;
        final Field dataHandlerDisabledField;
        final Field dataHandlerCountField;
        final Field adapterContextField;
        final Field adapterItemsField;
        final Field adapterRecentCountField;
        final Field adapterPinnedVisibleCountField;
        final Field clipItemViewHolderTextField;
        final Field clipIdField;
        final Field clipTimestampField;
        final Field clipModelField;
        final Field clipModelItemTypeField;
        final Method clipIsPinnedMethod;
        final Method clipIsSpecialMethod;
        final Method queryClipsMethod;
        final Method createClipboardUriMethod;
        final Method deleteClipsMethod;
        final Method primaryTimestampReadMethod;
        final Method dataHandlerQueryByItemTypeMethod;
        final Method dataHandlerCleanupCursorMethod;
        final Method preferencesAccessorMethod;
        final Method preferenceReadLongMethod;
        final Method preferenceWriteLongMethod;
        final Method adapterNotifyItemRemovedMethod;
        final Method adapterRefreshMethod;
        final Class<?> clipItemViewHolderClass;
        final Object recentHeader;
        final Object pinnedHeader;
        final Object specialHeader;

        ReflectionHandles(ClassLoader classLoader) throws Throwable {
            Class<?> loaderCallableClass = resolveClass(classLoader,
                    CLIPBOARD_LOADER_CALLABLE_CLASS);
            Class<?> loaderClass = resolveClass(classLoader, "elo");
            Class<?> pruneCallableClass = resolveClass(classLoader,
                    CLIPBOARD_PRUNE_CALLABLE_CLASS);
            Class<?> dataHandlerClass = resolveClass(classLoader, "emy");
            Class<?> adapterClass = resolveClass(classLoader, CLIPBOARD_ADAPTER_CLASS);
            Class<?> clipItemViewHolderClass = resolveClass(classLoader, CLIPBOARD_VIEW_HOLDER_CLASS);
            Class<?> clipClass = resolveClass(classLoader, "elk");
            Class<?> clipModelClass = resolveClass(classLoader, "elm");
            Class<?> queryUtilsClass = resolveClass(classLoader, "emo");
            Class<?> preferencesClass = resolveClass(classLoader, "oql");
            Class<?> preferenceBaseClass = resolveClass(classLoader, "bze");

            loaderCallableOwnerField = declaredField(loaderCallableClass, "a");
            loaderContextField = declaredField(loaderClass, "b");
            pruneCallableOwnerField = declaredField(pruneCallableClass, "a");
            dataHandlerContextField = declaredField(dataHandlerClass, "c");
            dataHandlerDisabledField = declaredField(dataHandlerClass, "g");
            dataHandlerCountField = declaredField(dataHandlerClass, "h");
            adapterContextField = declaredField(adapterClass, "d");
            adapterItemsField = declaredField(adapterClass, "m");
            adapterRecentCountField = declaredField(adapterClass, "n");
            adapterPinnedVisibleCountField = declaredField(adapterClass, "x");
            clipItemViewHolderTextField = declaredField(clipItemViewHolderClass, "t");
            clipIdField = declaredField(clipClass, "d");
            clipTimestampField = declaredField(clipClass, "e");
            clipModelField = declaredField(clipClass, "g");
            clipModelItemTypeField = declaredField(clipModelClass, "c");
            clipIsPinnedMethod = declaredMethod(clipClass, "m");
            clipIsSpecialMethod = declaredMethod(clipClass, "n");
            queryClipsMethod = declaredMethod(queryUtilsClass, "i",
                    Context.class, String.class, String[].class, String.class);
            createClipboardUriMethod = declaredMethod(queryUtilsClass, "c",
                    Context.class, int.class, long.class);
            deleteClipsMethod = declaredMethod(queryUtilsClass, "g",
                    Context.class, List.class);
            primaryTimestampReadMethod = declaredMethod(dataHandlerClass, "a",
                    Context.class);
            dataHandlerQueryByItemTypeMethod = declaredMethod(dataHandlerClass, "b",
                    Uri.class, int.class);
            dataHandlerCleanupCursorMethod = declaredMethod(dataHandlerClass, "k",
                    Cursor.class);
            preferencesAccessorMethod = declaredMethod(preferencesClass, "O", Context.class);
            preferenceReadLongMethod = declaredMethod(preferenceBaseClass, "m",
                    int.class, long.class);
            preferenceWriteLongMethod = declaredMethod(preferenceBaseClass, "r",
                    int.class, long.class);
            adapterNotifyItemRemovedMethod = declaredMethod(adapterClass.getSuperclass(),
                    "n", int.class);
            adapterRefreshMethod = declaredMethod(adapterClass, "R");
            this.clipItemViewHolderClass = clipItemViewHolderClass;
            recentHeader = declaredField(clipClass, "a").get(null);
            pinnedHeader = declaredField(clipClass, "b").get(null);
            specialHeader = declaredField(clipClass, "c").get(null);
        }

        private static Field declaredField(Class<?> owner, String name)
                throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static Method declaredMethod(Class<?> owner, String name,
                Class<?>... parameterTypes) throws NoSuchMethodException {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }

    private static Class<?> resolveClass(ClassLoader classLoader, String className)
            throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }
}
