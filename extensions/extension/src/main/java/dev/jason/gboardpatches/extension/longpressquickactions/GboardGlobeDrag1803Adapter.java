package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.View;

/** All 18.0.3 private names and event carriers used by globe drag. */
final class GboardGlobeDrag1803Adapter implements GboardGlobeDragPort {
    private static final String GLOBE_VIEW_NAME = "key_pos_qwerty_globe";
    private static final String NEXT_LANGUAGE_VIEW_NAME = "key_pos_switch_to_next_language";
    private static final int MARKER_DOWN = -0x2730;
    private static final int MARKER_UP = -0x2731;
    private static final int SWITCH_TO_NEXT_LANGUAGE = -0x271b;
    private static final int CHORD_EVENT_SUBTYPE = 3;
    private static final int FLOW_MODE_TYPING_PULSE_KEY_CODE = -10043;
    private static final int PLAIN_TEXT_KEY_CODE = -10009;

    private final GboardLongPressQuickActions1803ReflectionHandles handles;

    GboardGlobeDrag1803Adapter(
            GboardLongPressQuickActions1803ReflectionHandles handles) {
        if (handles == null) {
            throw new IllegalArgumentException("18.0.3 globe adapter requires handles");
        }
        this.handles = handles;
    }

    @Override
    public boolean isGlobeView(View view) {
        if (view == null) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(view.getId());
            return GLOBE_VIEW_NAME.equals(name) || NEXT_LANGUAGE_VIEW_NAME.equals(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Object patchGlobeMetadata(Object metadata, Object marker) throws Throwable {
        if (!handles.isStockGlobeMetadata(metadata)) {
            return null;
        }
        return handles.appendShiftChordActions(metadata, MARKER_DOWN, marker, MARKER_UP);
    }

    @Override
    public Object extractSoftKeyMetadata(Object softKeyView) throws Throwable {
        return handles.extractSoftKeyMetadata(softKeyView);
    }

    @Override
    public InputSignal inspectInputEvent(Object event, Object marker) throws Throwable {
        Object metadata = handles.extractEventMetadata(event);
        String actionType = handles.extractEventActionTypeName(event);
        int code = handles.extractSelectedEventCode(event);
        Object payload = handles.extractSelectedEventPayload(event);
        boolean markerDown = "DOWN".equals(actionType)
                && code == MARKER_DOWN && marker.equals(payload);
        boolean markerUp = "UP".equals(actionType) && code == MARKER_UP;
        int subtype = requiresEventSubtype(markerDown, markerUp)
                ? handles.extractEventSubtype(event) : 0;
        return new InputSignal(
                actionType,
                code,
                metadata,
                markerDown,
                markerUp,
                "PRESS".equals(actionType) && code == SWITCH_TO_NEXT_LANGUAGE,
                subtype == CHORD_EVENT_SUBTYPE);
    }

    static boolean requiresEventSubtype(boolean markerDown, boolean markerUp) {
        return !markerDown && !markerUp;
    }

    @Override
    public GestureSignal inspectGesture(Object actionType, Object entry, Object metadata)
            throws Throwable {
        String actionTypeName = handles.extractActionTypeName(actionType);
        int code = handles.extractEntryCode(entry);
        return new GestureSignal(
                actionTypeName,
                code,
                metadata,
                "PRESS".equals(actionTypeName) && code == SWITCH_TO_NEXT_LANGUAGE,
                inspectTerminalTarget(metadata, code, handles.extractEntryPayload(entry)));
    }

    @Override
    public TargetSignal inspectPointerTarget(Object metadata) throws Throwable {
        int keyId = handles.extractKeyId(metadata);
        String pressText = handles.extractPressText(metadata);
        int pressCarrier = handles.extractPressCarrierCode(metadata);
        GboardEditingShortcutPolicy.Shortcut shortcut =
                GboardLongPressQuickActions1803Policy.shortcutForPointerTarget(
                        keyId, pressText, pressCarrier, FLOW_MODE_TYPING_PULSE_KEY_CODE);
        return new TargetSignal(
                keyId, pressText, Integer.valueOf(pressCarrier), shortcut, pressText != null);
    }

    @Override
    public TargetSignal inspectTerminalTarget(Object metadata, int selectedCode)
            throws Throwable {
        return inspectTerminalTarget(metadata, selectedCode, null);
    }

    private TargetSignal inspectTerminalTarget(Object metadata, int selectedCode,
            Object entryPayload) throws Throwable {
        int keyId = handles.extractKeyId(metadata);
        String pressText = handles.extractPressText(metadata);
        GboardEditingShortcutPolicy.Shortcut shortcut =
                GboardLongPressQuickActions1803Policy.shortcutForChord(
                        keyId,
                        pressText,
                        selectedCode,
                        FLOW_MODE_TYPING_PULSE_KEY_CODE,
                        PLAIN_TEXT_KEY_CODE);
        return new TargetSignal(
                keyId, pressText, entryPayload, shortcut, pressText != null);
    }

    @Override
    public boolean cancelScheduledLongPress(Object pointerTracker) throws Throwable {
        return handles.cancelScheduledLongPress(pointerTracker);
    }
}
