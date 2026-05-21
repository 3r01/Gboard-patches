package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GboardFeatureGroup implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String headerSummary;
    private final String emptyTitle;
    private final String emptySummary;
    private final List<GboardPatchesSettingsContract.Feature> children;

    public GboardFeatureGroup(String entryTitle,
            String entrySummary,
            String headerBadge,
            String headerSummary,
            String emptyTitle,
            String emptySummary,
            List<GboardPatchesSettingsContract.Feature> children) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.headerSummary = headerSummary;
        this.emptyTitle = emptyTitle;
        this.emptySummary = emptySummary;
        this.children = Collections.unmodifiableList(
                new ArrayList<GboardPatchesSettingsContract.Feature>(children));
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
        return !availableChildren(context).isEmpty();
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.Host host) {
        List<GboardPatchesSettingsContract.Feature> visibleChildren =
                availableChildren(host.getContext());
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        if (visibleChildren.isEmpty()) {
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    emptyTitle,
                    emptySummary,
                    false));
        } else {
            for (GboardPatchesSettingsContract.Feature child : visibleChildren) {
                rows.add(new GboardPatchesSettingsContract.ActionRow(
                        child.getEntryTitle(),
                        child.getEntrySummary(),
                        true,
                        new OpenChildFeatureAction(host, child)));
            }
        }
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                headerSummary,
                rows);
    }

    private List<GboardPatchesSettingsContract.Feature> availableChildren(Context context) {
        List<GboardPatchesSettingsContract.Feature> visible =
                new ArrayList<GboardPatchesSettingsContract.Feature>();
        for (GboardPatchesSettingsContract.Feature child : children) {
            if (child == null) {
                continue;
            }
            try {
                if (child.isAvailable(context)) {
                    visible.add(child);
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to evaluate grouped feature availability: "
                        + child.getClass().getName(), throwable);
            }
        }
        return visible;
    }

    private static final class OpenChildFeatureAction implements Runnable {
        private final GboardPatchesSettingsContract.Host host;
        private final GboardPatchesSettingsContract.Feature child;

        OpenChildFeatureAction(GboardPatchesSettingsContract.Host host,
                GboardPatchesSettingsContract.Feature child) {
            this.host = host;
            this.child = child;
        }

        @Override
        public void run() {
            if (host == null || child == null) {
                return;
            }
            host.openFeature(child);
        }
    }
}
