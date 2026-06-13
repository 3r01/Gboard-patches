package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.R;

public final class GboardSettingsTextTest {
    @Test
    public void stableCopyReturnsTraditionalChineseForKnownShellStrings() {
        Assert.assertEquals(
                "Patch 設定",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "zh-Hant",
                        "Patch settings"));
        Assert.assertEquals(
                "偏好",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_section_preferences,
                        "zh-Hant",
                        "Preferences"));
        Assert.assertEquals(
                "語言",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_language_title,
                        "zh-Hant",
                        "Language"));
    }

    @Test
    public void stableCopyFallsBackToEnglishForKnownStringsOutsideTraditionalChinese() {
        Assert.assertEquals(
                "Patch settings",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "en",
                        "Patch settings"));
        Assert.assertEquals(
                "Current",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_current_value_label,
                        "en",
                        "Current"));
        Assert.assertEquals(
                "Preview",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_preview_label,
                        "en",
                        "Preview"));
    }

    @Test
    public void stableCopyFormatsLanguageAndLatinGlobeValues() {
        Assert.assertEquals(
                "跟隨系統（繁體中文）",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_language_system_value,
                        "zh-Hant",
                        "System default (%1$s)",
                        "繁體中文"));
        Assert.assertEquals(
                "500 ms（預設）",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_latin_globe_interval_value_default,
                        "zh-Hant",
                        "%1$d ms (Default)",
                        500));
        Assert.assertEquals(
                "500 ms (Default)",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_latin_globe_interval_value_default,
                        "en",
                        "%1$d ms (Default)",
                        500));
    }

    @Test
    public void stableCopyReturnsTraditionalChineseForClipboardAndSettingsHomepageStrings() {
        Assert.assertEquals(
                "剪貼簿",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_clipboard_title,
                        "zh-Hant",
                        "Clipboard"));
        Assert.assertEquals(
                "網頁剪貼簿",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_web_clipboard_title,
                        "zh-Hant",
                        "Web Clipboard"));
        Assert.assertEquals(
                "設定樣式",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_settings_homepage_title,
                        "zh-Hant",
                        "Settings Style"));
        Assert.assertEquals(
                "表情符號、貼圖與 GIF 分頁順序",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_symbol_footer_title,
                        "zh-Hant",
                        "Emojis, stickers & GIFs Tab Order"));
        Assert.assertEquals(
                "貼圖",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_symbol_tab_sticker,
                        "zh-Hant",
                        "Sticker"));
    }
}
