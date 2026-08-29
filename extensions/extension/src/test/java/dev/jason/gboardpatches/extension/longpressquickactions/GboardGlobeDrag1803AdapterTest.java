package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardGlobeDrag1803AdapterTest {
    @Test
    public void syntheticMarkerEventsDoNotDependOnLazyChordSubtypeBinding() {
        Assert.assertFalse(GboardGlobeDrag1803Adapter.requiresEventSubtype(true, false));
        Assert.assertFalse(GboardGlobeDrag1803Adapter.requiresEventSubtype(false, true));
        Assert.assertTrue(GboardGlobeDrag1803Adapter.requiresEventSubtype(false, false));
    }
}
