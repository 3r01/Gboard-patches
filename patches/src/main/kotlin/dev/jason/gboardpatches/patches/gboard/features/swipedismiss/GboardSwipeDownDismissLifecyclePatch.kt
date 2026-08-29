package dev.jason.gboardpatches.patches.gboard.features.swipedismiss

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private val onStartInputView = GboardMethodTarget(
    "Loup;",
    "onStartInputView",
    listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
    "V",
)

internal val gboardSwipeDownDismissLifecyclePatch = bytecodePatch(
    description = "Install the swipe-down header gesture observer after the input view starts.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(onStartInputView).applySwipeDownDismissExitDelegate()
    }
}

internal fun MutableMethod.applySwipeDownDismissExitDelegate() {
    val call = RuntimeCallId.SWIPE_DOWN_DISMISS_RUNTIME_ON_INPUT_VIEW_STARTED
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)?.isMethodReference(abi.reference) == true
        }) { "Malformed swipe-down dismiss exit delegate in $definingClass->$name" }
        return
    }
    returns.asReversed().forEach { returnIndex ->
        addInstructions(
            returnIndex,
            RuntimeCallEmitter.invoke(call, "p0 .. p0"),
        )
    }
}
