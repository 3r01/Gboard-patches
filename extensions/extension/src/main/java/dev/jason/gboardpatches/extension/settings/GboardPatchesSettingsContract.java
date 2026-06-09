package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GboardPatchesSettingsContract {
    public interface Feature {
        String getEntryTitle();

        String getEntrySummary();

        default boolean isAvailable(Context context) {
            return true;
        }

        Screen buildScreen(Host host);
    }

    public interface Host {
        Context getContext();

        void refresh();

        void openFeature(Feature feature);

        void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                StringValueConsumer valueConsumer);

        void showPositiveIntegerDialog(String title, String hint, int initialValue,
                PositiveIntegerConsumer consumer);

        void showTextInputDialog(String title, String hint, String initialValue,
                TextValueConsumer consumer);

        void showImageDialog(PreviewSpec previewSpec);
    }

    public interface StringValueConsumer {
        void accept(String value);
    }

    public interface PositiveIntegerConsumer {
        void accept(int value);
    }

    public interface TextValueConsumer {
        void accept(String value);
    }

    public interface ToggleAction {
        void accept(boolean value);
    }

    public enum PreviewLayout {
        SIDE_BY_SIDE,
        STACKED
    }

    public static final class PreviewImage {
        private final String assetPath;
        private final String caption;

        public PreviewImage(String assetPath, String caption) {
            this.assetPath = assetPath;
            this.caption = caption;
        }

        public String getAssetPath() {
            return assetPath;
        }

        public String getCaption() {
            return caption;
        }
    }

    public static final class PreviewSpec {
        private final String title;
        private final String message;
        private final PreviewLayout layout;
        private final List<PreviewImage> images;

        public PreviewSpec(String title, String message, PreviewImage... images) {
            this(title, message, PreviewLayout.SIDE_BY_SIDE, Arrays.asList(images));
        }

        public PreviewSpec(String title, String message, PreviewLayout layout,
                PreviewImage... images) {
            this(title, message, layout, Arrays.asList(images));
        }

        public PreviewSpec(String title, String message, List<PreviewImage> images) {
            this(title, message, PreviewLayout.SIDE_BY_SIDE, images);
        }

        public PreviewSpec(String title, String message, PreviewLayout layout,
                List<PreviewImage> images) {
            this.title = title;
            this.message = message;
            this.layout = layout;
            this.images = Collections.unmodifiableList(new ArrayList<PreviewImage>(images));
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public PreviewLayout getLayout() {
            return layout;
        }

        public List<PreviewImage> getImages() {
            return images;
        }
    }

    public static final class Screen {
        private final String toolbarTitle;
        private final String headerBadge;
        private final String headerTitle;
        private final String headerSummary;
        private final List<Row> rows;
        private final long refreshIntervalMs;

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<Row> rows) {
            this(toolbarTitle, headerBadge, headerTitle, headerSummary, rows, 0L);
        }

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<Row> rows, long refreshIntervalMs) {
            this.toolbarTitle = toolbarTitle;
            this.headerBadge = headerBadge;
            this.headerTitle = headerTitle;
            this.headerSummary = headerSummary;
            this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
            this.refreshIntervalMs = refreshIntervalMs;
        }

        public String getToolbarTitle() {
            return toolbarTitle;
        }

        public String getHeaderBadge() {
            return headerBadge;
        }

        public String getHeaderTitle() {
            return headerTitle;
        }

        public String getHeaderSummary() {
            return headerSummary;
        }

        public List<Row> getRows() {
            return rows;
        }

        public long getRefreshIntervalMs() {
            return refreshIntervalMs;
        }
    }

    public abstract static class Row {
        private final String title;
        private final String summary;
        private final boolean enabled;
        private final PreviewSpec previewSpec;

        protected Row(String title, String summary, boolean enabled) {
            this(title, summary, enabled, null);
        }

        protected Row(String title, String summary, boolean enabled, PreviewSpec previewSpec) {
            this.title = title;
            this.summary = summary;
            this.enabled = enabled;
            this.previewSpec = previewSpec;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public PreviewSpec getPreviewSpec() {
            return previewSpec;
        }
    }

    public static final class ActionRow extends Row {
        private final Runnable action;
        private final boolean showChevron;

        public ActionRow(String title, String summary, boolean enabled, Runnable action) {
            this(title, summary, enabled, true, action, null);
        }

        public ActionRow(String title, String summary, boolean enabled, boolean showChevron,
                Runnable action) {
            this(title, summary, enabled, showChevron, action, null);
        }

        public ActionRow(String title, String summary, boolean enabled, Runnable action,
                PreviewSpec previewSpec) {
            this(title, summary, enabled, true, action, previewSpec);
        }

        public ActionRow(String title, String summary, boolean enabled, boolean showChevron,
                Runnable action, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.action = action;
            this.showChevron = showChevron;
        }

        public Runnable getAction() {
            return action;
        }

        public boolean shouldShowChevron() {
            return showChevron;
        }
    }

    public static final class InfoRow extends Row {
        public InfoRow(String title, String summary, boolean enabled) {
            super(title, summary, enabled);
        }

        public InfoRow(String title, String summary, boolean enabled, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
        }
    }

    public static final class SwitchRow extends Row {
        private final boolean checked;
        private final ToggleAction toggleAction;

        public SwitchRow(String title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction) {
            this(title, summary, enabled, checked, toggleAction, null);
        }

        public SwitchRow(String title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.checked = checked;
            this.toggleAction = toggleAction;
        }

        public boolean isChecked() {
            return checked;
        }

        public ToggleAction getToggleAction() {
            return toggleAction;
        }
    }

    private GboardPatchesSettingsContract() {
    }
}
