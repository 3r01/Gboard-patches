package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class GboardGlobeDragSessionTest {
    private static final long GRACE_MS = 750L;

    @Test
    public void invalidClaimCommitsAsNoOpWithoutCallingEditor() {
        AtomicInteger calls = new AtomicInteger();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                1, new Object(), shortcut -> {
                    calls.incrementAndGet();
                    return true;
                }, GRACE_MS);
        session.state.onTargetOwner(null);

        GboardGlobeDragSession.CommitResult result = session.commitClaimedTarget(1_000L);

        Assert.assertTrue(result.consumed);
        Assert.assertFalse(result.actionSucceeded);
        Assert.assertEquals(0, calls.get());
        Assert.assertTrue(session.state.isAwaitingReplay(1_500L));
    }

    @Test
    public void editorFailureStillConsumesAndCleansSession() {
        RuntimeException failure = new RuntimeException("editor failed");
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                2, new Object(), shortcut -> { throw failure; }, GRACE_MS);
        session.state.onTargetOwner(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL);

        GboardGlobeDragSession.CommitResult result = session.commitClaimedTarget(2_000L);

        Assert.assertTrue(result.consumed);
        Assert.assertFalse(result.actionSucceeded);
        Assert.assertSame(failure, result.failure);
        Assert.assertNull(session.tracker);
        Assert.assertTrue(session.state.isAwaitingReplay(2_500L));
    }

    @Test
    public void pointerFinishClearsMissingReplayTrackerRegression() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                3, tracker, shortcut -> true, GRACE_MS);
        session.commitTarget(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL, 3_000L);
        Assert.assertTrue(session.shouldConsumeGestureReplay(tracker, 3_100L));

        session.onPointerFinish(tracker, 3_200L);

        Assert.assertFalse(session.shouldConsumeGestureReplay(tracker, 3_300L));
        Assert.assertTrue(session.state.isAwaitingReplay(3_300L));
    }

    @Test
    public void invalidShiftEndpointSurvivesFinishThenResetUntilTerminal() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                4, tracker, shortcut -> true, GRACE_MS);
        session.state.onTargetOwner(null);

        Assert.assertFalse(session.hasActionableClaim());
        session.onPointerFinish(tracker, 4_000L);
        Assert.assertTrue(session.state.isAwaitingTerminal(4_100L));

        Assert.assertTrue(session.retainAcrossPointerCleanup(tracker, 4_100L));
        Assert.assertTrue(session.state.isAwaitingTerminal(4_200L));
        Assert.assertFalse(session.state.isComplete());
    }

    @Test
    public void activeSessionDoesNotSurviveExplicitPointerCancel() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                5, tracker, shortcut -> true, GRACE_MS);

        Assert.assertFalse(session.retainAcrossPointerCleanup(tracker, 5_000L));
        Assert.assertTrue(session.state.isActive());
    }

    @Test
    public void replayGraceSurvivesResetButDropsGestureTracker() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                6, tracker, shortcut -> true, GRACE_MS);
        session.commitTarget(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL, 6_000L);
        Assert.assertTrue(session.shouldConsumeGestureReplay(tracker, 6_100L));

        Assert.assertTrue(session.retainAcrossPointerCleanup(tracker, 6_200L));

        Assert.assertFalse(session.shouldConsumeGestureReplay(tracker, 6_300L));
        Assert.assertTrue(session.state.isAwaitingReplay(6_300L));
    }
}
