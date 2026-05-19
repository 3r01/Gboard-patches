package dev.jason.gboardpatches.patches.gboard.features.englishqwerty

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.indexOfFirstConst4LiteralFollowedByIfEqz
import dev.jason.gboardpatches.patches.gboard.shared.indexOfFirstMethodCall

private const val SOFT_KEY_VIEW_CLASS =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"

internal val gboardEnglishQwertySoftKeyPatch = bytecodePatch(
    description = "修正英文 QWERTY 的 SoftKeyView metadata 與提示呈現。"
) {
    execute {
        addHelperMethods()
        injectMethodDelegates()
    }
}

context(context: BytecodePatchContext)
private fun addHelperMethods() = with(context) {
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevToggleAsciiCase",
        parameterTypes = listOf("Ljava/lang/String;"),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 3,
        body = TOGGLE_ASCII_CASE_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevResolveActionText",
        parameterTypes = listOf("Loaa;", "Lnxi;"),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 4,
        body = RESOLVE_ACTION_TEXT_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevResolvePrimaryLabel",
        parameterTypes = listOf("Loaa;"),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 7,
        body = RESOLVE_PRIMARY_LABEL_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevIsEnglishQwertyKeyId",
        parameterTypes = listOf("I"),
        returnType = "Z",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 2,
        body = IS_ENGLISH_QWERTY_KEY_ID_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevResolveEnglishSlideDown",
        parameterTypes = listOf("Ljava/lang/String;"),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 3,
        body = RESOLVE_ENGLISH_SLIDE_DOWN_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevSyncSyntheticEnglishHint",
        parameterTypes = listOf("Loaa;"),
        returnType = "V",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 12,
        body = SYNC_SYNTHETIC_ENGLISH_HINT_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevSyncEnglishHintView",
        parameterTypes = listOf("Loaa;"),
        returnType = "V",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 9,
        body = SYNC_ENGLISH_HINT_VIEW_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevSyncPatchedPresentation",
        parameterTypes = listOf("Loaa;"),
        returnType = "V",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 2,
        body = SYNC_PATCHED_PRESENTATION_BODY
    )
    addHelperMethodIfMissing(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "jasondevPatchIncomingMetadata",
        parameterTypes = listOf("Loaa;"),
        returnType = "Loaa;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 14,
        body = PATCH_INCOMING_METADATA_BODY
    )
}

context(context: BytecodePatchContext)
private fun injectMethodDelegates() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = SOFT_KEY_VIEW_CLASS,
        name = "p",
        returnType = "Z",
        parameterTypes = listOf("Loaa;", "J")
    )

    mutableMethod.addInstructions(0, PATCH_INCOMING_METADATA_DELEGATE)

    var syncIndex = mutableMethod.indexOfFirstConst4LiteralFollowedByIfEqz(1)
    if (syncIndex < 0) {
        syncIndex = mutableMethod.indexOfFirstMethodCall(
            definingClass = "Loaa;",
            name = "f",
            returnType = "Z",
            parameterTypes = listOf("Lnxi;")
        )
    }
    check(syncIndex >= 0) { "Unable to find SoftKeyView.p sync anchor" }
    mutableMethod.addInstructions(syncIndex, SYNC_PATCHED_PRESENTATION_DELEGATE)
}

private val PATCH_INCOMING_METADATA_DELEGATE = """
    invoke-direct {p0, p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevPatchIncomingMetadata(Loaa;)Loaa;

    move-result-object p1
""".trimIndent()

private val SYNC_PATCHED_PRESENTATION_DELEGATE = """
    invoke-direct {p0, p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevSyncPatchedPresentation(Loaa;)V
""".trimIndent()

private val TOGGLE_ASCII_CASE_BODY = """
    if-eqz p0, :cond_2

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    const/4 v1, 0x1

    if-eq v0, v1, :cond_0

    goto :cond_2

    :cond_0
    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Ljava/lang/String;->charAt(I)C

    move-result v0

    const/16 v1, 0x61

    if-lt v0, v1, :cond_1

    const/16 v1, 0x7a

    if-gt v0, v1, :cond_1

    add-int/lit8 v0, v0, -0x20

    int-to-char v0, v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(C)Ljava/lang/String;

    move-result-object p0

    return-object p0

    :cond_1
    const/16 v1, 0x41

    if-lt v0, v1, :cond_2

    const/16 v1, 0x5a

    if-gt v0, v1, :cond_2

    add-int/lit8 v0, v0, 0x20

    int-to-char v0, v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(C)Ljava/lang/String;

    move-result-object p0

    return-object p0

    :cond_2
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val RESOLVE_ACTION_TEXT_BODY = """
    if-eqz p0, :cond_2

    if-eqz p1, :cond_2

    invoke-virtual {p0, p1}, Loaa;->a(Lnxi;)Lnxl;

    move-result-object p0

    if-eqz p0, :cond_2

    invoke-virtual {p0}, Lnxl;->b()Lnyf;

    move-result-object p0

    if-eqz p0, :cond_2

    iget-object p0, p0, Lnyf;->e:Ljava/lang/Object;

    instance-of p1, p0, Ljava/lang/CharSequence;

    if-eqz p1, :cond_2

    invoke-virtual {p0}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0

    :cond_2
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val RESOLVE_PRIMARY_LABEL_BODY = """
    if-eqz p0, :cond_2

    iget-object v0, p0, Loaa;->o:[I

    if-eqz v0, :cond_2

    iget-object p0, p0, Loaa;->n:[Ljava/lang/CharSequence;

    if-eqz p0, :cond_2

    array-length v1, v0

    array-length v2, p0

    if-gt v1, v2, :cond_0

    move v2, v1

    :cond_0
    const/4 v1, 0x0

    :goto_0
    if-ge v1, v2, :cond_2

    aget v3, v0, v1

    const v4, 0x7f0b0607

    if-ne v3, v4, :cond_1

    aget-object v3, p0, v1

    if-eqz v3, :cond_1

    invoke-virtual {v3}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v4

    if-eqz v4, :cond_1

    invoke-virtual {v4}, Ljava/lang/String;->length()I

    move-result v5

    if-gtz v5, :cond_return_label

    :cond_1
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_return_label
    return-object v4

    :cond_2
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val IS_ENGLISH_QWERTY_KEY_ID_BODY = """
    sparse-switch p0, :sswitch_data_0

    const/4 p0, 0x0

    return p0

    :sswitch_0
    const/4 p0, 0x1

    return p0

    :sswitch_data_0
    .sparse-switch
        0x7f0b1819 -> :sswitch_0
        0x7f0b1828 -> :sswitch_0
        0x7f0b182a -> :sswitch_0
        0x7f0b1831 -> :sswitch_0
        0x7f0b1836 -> :sswitch_0
        0x7f0b1848 -> :sswitch_0
        0x7f0b184a -> :sswitch_0
        0x7f0b1855 -> :sswitch_0
        0x7f0b185a -> :sswitch_0
        0x7f0b186b -> :sswitch_0
        0x7f0b186d -> :sswitch_0
        0x7f0b1871 -> :sswitch_0
        0x7f0b1875 -> :sswitch_0
        0x7f0b1879 -> :sswitch_0
        0x7f0b1882 -> :sswitch_0
        0x7f0b1897 -> :sswitch_0
        0x7f0b1899 -> :sswitch_0
        0x7f0b189a -> :sswitch_0
        0x7f0b189e -> :sswitch_0
        0x7f0b18a7 -> :sswitch_0
        0x7f0b18b1 -> :sswitch_0
        0x7f0b18c4 -> :sswitch_0
        0x7f0b18c6 -> :sswitch_0
        0x7f0b18c9 -> :sswitch_0
        0x7f0b18ca -> :sswitch_0
        0x7f0b18ce -> :sswitch_0
        0x7f0b18f5 -> :sswitch_0
        0x7f0b1905 -> :sswitch_0
        0x7f0b190a -> :sswitch_0
        0x7f0b1917 -> :sswitch_0
        0x7f0b1924 -> :sswitch_0
        0x7f0b193a -> :sswitch_0
        0x7f0b193e -> :sswitch_0
        0x7f0b194d -> :sswitch_0
        0x7f0b1953 -> :sswitch_0
        0x7f0b1964 -> :sswitch_0
        0x7f0b1968 -> :sswitch_0
        0x7f0b1970 -> :sswitch_0
        0x7f0b197c -> :sswitch_0
        0x7f0b1982 -> :sswitch_0
        0x7f0b198e -> :sswitch_0
        0x7f0b19a4 -> :sswitch_0
        0x7f0b19aa -> :sswitch_0
        0x7f0b19ad -> :sswitch_0
        0x7f0b19b7 -> :sswitch_0
        0x7f0b19df -> :sswitch_0
        0x7f0b19ff -> :sswitch_0
        0x7f0b1a14 -> :sswitch_0
        0x7f0b1a17 -> :sswitch_0
        0x7f0b1a1b -> :sswitch_0
        0x7f0b1a1e -> :sswitch_0
        0x7f0b1a24 -> :sswitch_0
    .end sparse-switch
""".trimIndent()

private val RESOLVE_ENGLISH_SLIDE_DOWN_BODY = """
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    const/4 v1, 0x1

    if-eq v0, v1, :cond_1

    :cond_0
    const/4 p0, 0x0

    return-object p0

    :cond_1
    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Ljava/lang/String;->charAt(I)C

    move-result v0

    const/16 v1, 0x41

    if-lt v0, v1, :cond_2

    const/16 v1, 0x5a

    if-gt v0, v1, :cond_2

    add-int/lit8 v0, v0, 0x20

    int-to-char v0, v0

    :cond_2
    packed-switch v0, :pswitch_data_0

    const/4 p0, 0x0

    return-object p0

    :pswitch_a
    const-string p0, "@"

    return-object p0

    :pswitch_b
    const-string p0, "!"

    return-object p0

    :pswitch_c
    const-string p0, "\u0022"

    return-object p0

    :pswitch_d
    const-string p0, "+"

    return-object p0

    :pswitch_e
    const-string p0, "3"

    return-object p0

    :pswitch_f
    const-string p0, "-"

    return-object p0

    :pswitch_g
    const-string p0, "="

    return-object p0

    :pswitch_h
    const-string p0, "/"

    return-object p0

    :pswitch_i
    const-string p0, "8"

    return-object p0

    :pswitch_j
    const-string p0, "#"

    return-object p0

    :pswitch_k
    const-string p0, "("

    return-object p0

    :pswitch_l
    const-string p0, ")"

    return-object p0

    :pswitch_m
    const-string p0, "\u2026"

    return-object p0

    :pswitch_n
    const-string p0, "~"

    return-object p0

    :pswitch_o
    const-string p0, "9"

    return-object p0

    :pswitch_p
    const-string p0, "0"

    return-object p0

    :pswitch_q
    const-string p0, "1"

    return-object p0

    :pswitch_r
    const-string p0, "4"

    return-object p0

    :pswitch_s
    const-string p0, "*"

    return-object p0

    :pswitch_t
    const-string p0, "5"

    return-object p0

    :pswitch_u
    const-string p0, "7"

    return-object p0

    :pswitch_v
    const-string p0, "?"

    return-object p0

    :pswitch_w
    const-string p0, "2"

    return-object p0

    :pswitch_x
    const-string p0, ":"

    return-object p0

    :pswitch_y
    const-string p0, "6"

    return-object p0

    :pswitch_z
    const-string p0, "\u0027"

    return-object p0

    :pswitch_data_0
    .packed-switch 0x61
        :pswitch_a
        :pswitch_b
        :pswitch_c
        :pswitch_d
        :pswitch_e
        :pswitch_f
        :pswitch_g
        :pswitch_h
        :pswitch_i
        :pswitch_j
        :pswitch_k
        :pswitch_l
        :pswitch_m
        :pswitch_n
        :pswitch_o
        :pswitch_p
        :pswitch_q
        :pswitch_r
        :pswitch_s
        :pswitch_t
        :pswitch_u
        :pswitch_v
        :pswitch_w
        :pswitch_x
        :pswitch_y
        :pswitch_z
    .end packed-switch
""".trimIndent()

private val SYNC_SYNTHETIC_ENGLISH_HINT_BODY = """
    iget-object v0, p0, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->m:Landroid/view/ViewGroup;

    instance-of v1, v0, Landroid/widget/FrameLayout;

    if-eqz v1, :cond_0

    check-cast v0, Landroid/widget/FrameLayout;

    goto :goto_0

    :cond_0
    move-object v0, p0

    :goto_0
    const-string v1, "jasondev:english-qwerty-hint"

    invoke-virtual {v0, v1}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v2

    iget v3, p1, Loaa;->c:I

    invoke-static {v3}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevIsEnglishQwertyKeyId(I)Z

    move-result v3

    if-eqz v3, :cond_remove

    invoke-static {p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolvePrimaryLabel(Loaa;)Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :cond_remove

    invoke-static {v3}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevToggleAsciiCase(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    if-eqz v4, :cond_remove

    sget-object v4, Lnxi;->d:Lnxi;

    invoke-static {p1, v4}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolveActionText(Loaa;Lnxi;)Ljava/lang/String;

    move-result-object p1

    if-eqz p1, :cond_remove

    instance-of v4, v2, Landroid/widget/TextView;

    if-eqz v4, :cond_create

    move-object v4, v2

    check-cast v4, Landroid/widget/TextView;

    goto :goto_1

    :cond_create
    new-instance v4, Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->getContext()Landroid/content/Context;

    move-result-object v5

    invoke-direct {v4, v5}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v4, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V

    const/4 v1, 0x0

    invoke-virtual {v4, v1}, Landroid/view/View;->setClickable(Z)V

    invoke-virtual {v4, v1}, Landroid/view/View;->setFocusable(Z)V

    invoke-virtual {v4, v1}, Landroid/widget/TextView;->setIncludeFontPadding(Z)V

    const v5, 0x3f147ae1

    invoke-virtual {v4, v5}, Landroid/view/View;->setAlpha(F)V

    const/4 v5, 0x2

    const/high16 v6, 0x41380000

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    const/4 v5, 0x1

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setSingleLine(Z)V

    const/4 v6, 0x6

    invoke-virtual {v4, v6}, Landroid/widget/TextView;->setTextAlignment(I)V

    const v6, 0x800035

    invoke-virtual {v4, v6}, Landroid/widget/TextView;->setGravity(I)V

    new-instance v7, Landroid/widget/FrameLayout${'$'}LayoutParams;

    const/4 v8, -0x2

    invoke-direct {v7, v8, v8, v6}, Landroid/widget/FrameLayout${'$'}LayoutParams;-><init>(III)V

    invoke-virtual {v4, v7}, Landroid/widget/TextView;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

    invoke-virtual {v0, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    goto :goto_1

    :cond_remove
    if-eqz v2, :cond_3

    invoke-virtual {v0, v2}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

    goto :cond_3

    :goto_1
    const v0, 0x7f0b0607

    invoke-virtual {p0, v0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->findViewById(I)Landroid/view/View;

    move-result-object v0

    instance-of v1, v0, Landroid/widget/TextView;

    if-eqz v1, :cond_1

    check-cast v0, Landroid/widget/TextView;

    invoke-virtual {v0}, Landroid/widget/TextView;->getTextColors()Landroid/content/res/ColorStateList;

    move-result-object v0

    invoke-virtual {v4, v0}, Landroid/widget/TextView;->setTextColor(Landroid/content/res/ColorStateList;)V

    :cond_1
    invoke-virtual {v4, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const/4 p1, 0x0

    invoke-virtual {v4, p1}, Landroid/view/View;->setVisibility(I)V

    const/4 v0, 0x1

    invoke-virtual {v4, v0}, Landroid/view/View;->setImportantForAccessibility(I)V

    const v0, 0x7f0b060f

    invoke-virtual {p0, v0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->findViewById(I)Landroid/view/View;

    move-result-object p0

    if-eqz p0, :cond_3

    if-ne p0, v4, :cond_2

    goto :cond_3

    :cond_2
    const/4 p1, 0x4

    invoke-virtual {p0, p1}, Landroid/view/View;->setVisibility(I)V

    :cond_3
    return-void
""".trimIndent()

private val SYNC_ENGLISH_HINT_VIEW_BODY = """
    iget v0, p1, Loaa;->c:I

    invoke-static {v0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevIsEnglishQwertyKeyId(I)Z

    move-result v0

    if-eqz v0, :cond_2

    invoke-static {p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolvePrimaryLabel(Loaa;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_2

    invoke-static {v0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevToggleAsciiCase(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_2

    sget-object v1, Lnxi;->d:Lnxi;

    invoke-static {p1, v1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolveActionText(Loaa;Lnxi;)Ljava/lang/String;

    move-result-object p1

    if-eqz p1, :cond_2

    const v1, 0x7f0b060f

    invoke-virtual {p0, v1, p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->o(ILjava/lang/CharSequence;)V

    invoke-virtual {p0, v1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->findViewById(I)Landroid/view/View;

    move-result-object v1

    if-eqz v1, :cond_2

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V

    instance-of v3, v1, Landroid/widget/TextView;

    if-eqz v3, :cond_2

    move-object v3, v1

    check-cast v3, Landroid/widget/TextView;

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Landroid/widget/TextView;->setSingleLine(Z)V

    const/4 v5, 0x6

    invoke-virtual {v3, v5}, Landroid/widget/TextView;->setTextAlignment(I)V

    const v5, 0x800005

    invoke-virtual {v3, v5}, Landroid/widget/TextView;->setGravity(I)V

    const v5, 0x7f0b0607

    invoke-virtual {p0, v5}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->findViewById(I)Landroid/view/View;

    move-result-object p0

    instance-of v5, p0, Landroid/widget/TextView;

    if-eqz v5, :cond_0

    check-cast p0, Landroid/widget/TextView;

    invoke-virtual {p0}, Landroid/widget/TextView;->getTextColors()Landroid/content/res/ColorStateList;

    move-result-object p0

    invoke-virtual {v3, p0}, Landroid/widget/TextView;->setTextColor(Landroid/content/res/ColorStateList;)V

    :cond_0
    invoke-virtual {v1, v4}, Landroid/view/View;->setImportantForAccessibility(I)V

    :cond_2
    return-void
""".trimIndent()

private val SYNC_PATCHED_PRESENTATION_BODY = """
    invoke-direct {p0, p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevSyncEnglishHintView(Loaa;)V

    invoke-direct {p0, p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevSyncSyntheticEnglishHint(Loaa;)V

    return-void
""".trimIndent()

private val PATCH_INCOMING_METADATA_BODY = """
    if-eqz p1, :cond_return_original

    iget v0, p1, Loaa;->c:I

    invoke-static {v0}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevIsEnglishQwertyKeyId(I)Z

    move-result v0

    if-eqz v0, :cond_return_original

    invoke-static {p1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolvePrimaryLabel(Loaa;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_return_original

    invoke-static {v1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevToggleAsciiCase(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    if-eqz v2, :cond_return_original

    invoke-static {v1}, Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;->jasondevResolveEnglishSlideDown(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :cond_return_original

    new-instance v4, Lnzv;

    invoke-direct {v4}, Lnzv;-><init>()V

    invoke-virtual {v4, p1}, Lnzv;->j(Loaa;)V

    new-instance v5, Lnxj;

    invoke-direct {v5}, Lnxj;-><init>()V

    sget-object v6, Lnxi;->c:Lnxi;

    iput-object v6, v5, Lnxj;->a:Lnxi;

    const/4 v6, 0x1

    new-array v7, v6, [Ljava/lang/String;

    const/4 v6, 0x0

    aput-object v2, v7, v6

    iput-object v7, v5, Lnxj;->c:[Ljava/lang/String;

    const v7, -0x2719

    sget-object v8, Lnye;->b:Lnye;

    invoke-virtual {v5, v7, v8, v2}, Lnxj;->p(ILnye;Ljava/lang/Object;)V

    invoke-virtual {v5}, Lnxj;->c()Lnxl;

    move-result-object v5

    if-eqz v5, :cond_return_original

    invoke-virtual {v4, v5}, Lnzv;->q(Lnxl;)V

    new-instance v5, Lnxj;

    invoke-direct {v5}, Lnxj;-><init>()V

    sget-object v9, Lnxi;->d:Lnxi;

    iput-object v9, v5, Lnxj;->a:Lnxi;

    const/4 v9, 0x1

    new-array v10, v9, [Ljava/lang/String;

    aput-object v3, v10, v6

    iput-object v10, v5, Lnxj;->c:[Ljava/lang/String;

    invoke-virtual {v5, v7, v8, v3}, Lnxj;->p(ILnye;Ljava/lang/Object;)V

    invoke-virtual {v5}, Lnxj;->c()Lnxl;

    move-result-object v5

    if-eqz v5, :cond_return_original

    invoke-virtual {v4, v5}, Lnzv;->q(Lnxl;)V

    const/4 v5, 0x2

    new-array v7, v5, [I

    new-array v8, v5, [Ljava/lang/CharSequence;

    const v5, 0x7f0b0607

    aput v5, v7, v6

    aput-object v1, v8, v6

    const/4 v1, 0x1

    const v5, 0x7f0b060f

    aput v5, v7, v1

    aput-object v3, v8, v1

    invoke-virtual {v4, v7, v8}, Lnzv;->t([I[Ljava/lang/CharSequence;)V

    invoke-virtual {v4}, Lnzv;->d()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Loaa;

    if-eqz v0, :cond_return_original

    return-object v0

    :cond_return_original
    return-object p1
""".trimIndent()
