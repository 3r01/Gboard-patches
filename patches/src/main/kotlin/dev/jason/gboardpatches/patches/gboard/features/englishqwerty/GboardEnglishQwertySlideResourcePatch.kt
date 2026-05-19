package dev.jason.gboardpatches.patches.gboard.features.englishqwerty

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.ensureTemplateAction
import dev.jason.gboardpatches.patches.gboard.shared.findSoftkeyList
import dev.jason.gboardpatches.patches.gboard.shared.findSoftkeyTemplate
import dev.jason.gboardpatches.patches.gboard.shared.setFooterLabelValue

private const val ENGLISH_QWERTY_XML = "res/xml/xml_0x7f171175.xml"
private const val ENGLISH_QWERTY_TEMPLATE_ID = "@id/id_0x7f0b21c5"
private const val FOOTER_LABEL_LOCATION = "@id/id_0x7f0b060f"
private const val ENGLISH_QWERTY_LAYOUT = "@layout/layout_0x7f0e0734"

private val ENGLISH_SLIDE_DOWN_MAP = linkedMapOf(
    "q" to "1",
    "w" to "2",
    "e" to "3",
    "r" to "4",
    "t" to "5",
    "y" to "6",
    "u" to "7",
    "i" to "8",
    "o" to "9",
    "p" to "0",
    "a" to "@",
    "s" to "*",
    "d" to "+",
    "f" to "-",
    "g" to "=",
    "h" to "/",
    "j" to "#",
    "k" to "(",
    "l" to ")",
    "z" to "'",
    "x" to ":",
    "c" to "\"",
    "v" to "?",
    "b" to "!",
    "n" to "~",
    "m" to "…"
)

internal val gboardEnglishQwertySlideResourcePatch = resourcePatch(
    description = "補回英文 QWERTY 的滑動輸入資源 metadata。"
) {
    finalize {
        applyEnglishQwertyResourcePatch()
    }
}

context(context: ResourcePatchContext)
private fun applyEnglishQwertyResourcePatch() = with(context) {
    document(ENGLISH_QWERTY_XML).use { document ->
        val template = document.findSoftkeyTemplate(ENGLISH_QWERTY_TEMPLATE_ID)
        template.setAttribute("layout", ENGLISH_QWERTY_LAYOUT)
        ensureTemplateAction(
            template,
            type = "SLIDE_UP",
            data = "\$slideup_data\$",
            popupLabel = "\$slideup_data\$"
        )
        ensureTemplateAction(
            template,
            type = "SLIDE_DOWN",
            data = "\$slidedown_data\$",
            popupLabel = "\$slidedown_data\$"
        )
        setFooterLabelValue(template, FOOTER_LABEL_LOCATION, "\$slidedown_data\$")

        document.findSoftkeyList(ENGLISH_QWERTY_TEMPLATE_ID)
            .childElements("softkey")
            .forEach { key ->
                val pressData = key.getAttribute("press_data")
                resolveEnglishSlideUpValue(pressData)?.let { key.setAttribute("slideup_data", it) }
                resolveEnglishSlideDownValue(pressData)?.let { key.setAttribute("slidedown_data", it) }
            }
    }
}

private fun resolveEnglishSlideUpValue(pressData: String): String? {
    if (pressData.length != 1) return null

    val char = pressData.single()
    return when {
        char in 'a'..'z' -> char.uppercaseChar().toString()
        char in 'A'..'Z' -> char.lowercaseChar().toString()
        else -> null
    }
}

private fun resolveEnglishSlideDownValue(pressData: String): String? =
    ENGLISH_SLIDE_DOWN_MAP[pressData.lowercase()]
