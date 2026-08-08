package dev.jason.gboardpatches.extension.ocr;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;

public final class GboardOcrSettingsFeatureTest {
    @Test
    public void selectorOffersEveryBackendPersistsChoiceAndRefreshes() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        CapturingHost host = new CapturingHost(preferences);
        GboardPatchesSettingsContract.Screen screen =
                new GboardOcrSettingsFeature(null).buildScreen(host);
        GboardPatchesSettingsContract.SelectorRow selector = findSelector(screen.getRows());

        Assert.assertNotNull(selector);
        Assert.assertEquals("", screen.getHeaderSummary());
        Assert.assertEquals("", selector.getSummary());
        Assert.assertEquals("Latin", selector.getCurrentValue());
        selector.getAction().run();
        Assert.assertArrayEquals(
                new String[]{"latin", "chinese", "japanese", "korean", "devanagari"},
                host.choiceValues);

        host.choiceConsumer.accept("chinese");
        Assert.assertEquals(GboardOcrEngine.CHINESE, GboardOcrSettings.readEngine(preferences));
        Assert.assertEquals(1, host.refreshCount);
    }

    @Test
    public void invalidStoredEngineIsSanitizedToLatin() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        preferences.edit().putString(GboardOcrSettings.PREF_KEY_ENGINE, "invalid").commit();

        GboardOcrSettings.ensureDefaults(preferences);

        Assert.assertEquals("latin",
                preferences.getString(GboardOcrSettings.PREF_KEY_ENGINE, null));
    }

    private static GboardPatchesSettingsContract.SelectorRow findSelector(
            List<GboardPatchesSettingsContract.Row> rows) {
        for (GboardPatchesSettingsContract.Row row : rows) {
            if (row instanceof GboardPatchesSettingsContract.SelectorRow selector) {
                return selector;
            }
        }
        return null;
    }

    private static final class CapturingHost extends GboardPatchesSettingsTestHost {
        private final Context context;
        private String[] choiceValues;
        private GboardPatchesSettingsContract.StringValueConsumer choiceConsumer;
        private int refreshCount;

        CapturingHost(SharedPreferences preferences) {
            context = new ContextWrapper(null) {
                @Override
                public Context getApplicationContext() {
                    return this;
                }

                @Override
                public SharedPreferences getSharedPreferences(String name, int mode) {
                    return preferences;
                }

                @Override
                public String getPackageName() {
                    return "dev.jason.gboardpatches.test";
                }
            };
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void refresh() {
            refreshCount++;
        }

        @Override
        public void openFeature(GboardPatchesSettingsContract.Feature feature) {
        }

        @Override
        public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
            choiceValues = values;
            choiceConsumer = valueConsumer;
        }

        @Override
        public void showPositiveIntegerDialog(String title, String hint, int initialValue,
                GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) {
        }

        @Override
        public void showTextInputDialog(String title, String hint, String initialValue,
                GboardPatchesSettingsContract.TextValueConsumer consumer) {
        }

        @Override
        public void showPreviewDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        }

        @Override
        public void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction) {
        }

        @Override
        public void openTextDocument(String[] mimeTypes,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        }
    }

    private static final class InMemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            return value instanceof Set ? (Set<String>) value : defaultValues;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).longValue() : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<String, Object>();

                @Override public Editor putString(String key, String value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor putStringSet(String key, Set<String> value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor putInt(String key, int value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor putLong(String key, long value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor putFloat(String key, float value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor putBoolean(String key, boolean value) {
                    pending.put(key, value); return this;
                }
                @Override public Editor remove(String key) {
                    pending.put(key, null); return this;
                }
                @Override public Editor clear() {
                    values.clear(); pending.clear(); return this;
                }
                @Override public boolean commit() {
                    apply(); return true;
                }
                @Override public void apply() {
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) values.remove(entry.getKey());
                        else values.put(entry.getKey(), entry.getValue());
                    }
                }
            };
        }

        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }
}
