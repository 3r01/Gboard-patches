package dev.jason.gboardpatches.extension.longpressquickactions;

/** Terminal ownership and editor dispatch for one globe drag. */
final class GboardGlobeDragSession {
    final int id;
    Object tracker;
    final GboardGlobeDragGestureState state;
    private final ShortcutExecutor shortcutExecutor;
    private Object replayGestureTracker;

    GboardGlobeDragSession(int id, Object tracker,
            ShortcutExecutor shortcutExecutor, long releaseGraceMs) {
        this.id = id;
        this.tracker = tracker;
        this.shortcutExecutor = shortcutExecutor;
        state = new GboardGlobeDragGestureState(releaseGraceMs);
    }

    CommitResult commitClaimedTarget(long now) {
        CommitResult result = commitTarget(state.targetShortcut(), now);
        replayGestureTracker = null;
        return result;
    }

    boolean hasActionableClaim() {
        return state.hasClaimedTarget() && state.targetShortcut() != null;
    }

    CommitResult commitTarget(GboardEditingShortcutPolicy.Shortcut shortcut, long now) {
        boolean actionSucceeded = false;
        Throwable failure = null;
        Object terminalTracker = state.isActive() ? tracker : null;
        try {
            if (shortcut != null && shortcutExecutor != null) {
                actionSucceeded = shortcutExecutor.perform(shortcut);
            }
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            state.onTerminalConsumed(now);
            replayGestureTracker = terminalTracker;
            tracker = null;
        }
        return new CommitResult(true, actionSucceeded, shortcut, failure);
    }

    boolean shouldConsumeGestureReplay(Object candidateTracker, long now) {
        return candidateTracker != null && candidateTracker == replayGestureTracker
                && state.isAwaitingReplay(now);
    }

    void onPointerFinish(Object pointerTracker, long now) {
        if (replayGestureTracker == pointerTracker) {
            replayGestureTracker = null;
        }
        state.onPointerFinish(now);
        tracker = null;
    }

    boolean retainAcrossPointerCleanup(Object pointerTracker, long now) {
        if (state.isAwaitingTerminal(now)) {
            tracker = null;
            return true;
        }
        if (state.isAwaitingReplay(now)) {
            onPointerFinish(pointerTracker, now);
            return true;
        }
        return false;
    }

    void abort() {
        state.onReplayConsumed();
        replayGestureTracker = null;
        tracker = null;
    }

    void failClosed(long now) {
        state.onFailure(now);
        if (!state.isFailedActive()) {
            replayGestureTracker = null;
            tracker = null;
        }
    }

    interface ShortcutExecutor {
        boolean perform(GboardEditingShortcutPolicy.Shortcut shortcut) throws Throwable;
    }

    static final class CommitResult {
        final boolean consumed;
        final boolean actionSucceeded;
        final GboardEditingShortcutPolicy.Shortcut shortcut;
        final Throwable failure;

        CommitResult(boolean consumed, boolean actionSucceeded,
                GboardEditingShortcutPolicy.Shortcut shortcut, Throwable failure) {
            this.consumed = consumed;
            this.actionSucceeded = actionSucceeded;
            this.shortcut = shortcut;
            this.failure = failure;
        }
    }
}
