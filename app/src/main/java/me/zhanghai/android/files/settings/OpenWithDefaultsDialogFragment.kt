/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.OpenWithDefault
import me.zhanghai.android.files.filelist.OpenWithDefaults
import me.zhanghai.android.files.filelist.getOpenWithLabel
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.valueCompat
import java.net.URI

/** Lists the remembered "Open with" apps and lets the user remove them. */
class OpenWithDefaultsDialogFragment : AppCompatDialogFragment() {
    private lateinit var defaults: List<OpenWithDefault>

    private lateinit var checkedItems: BooleanArray

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        defaults = Settings.OPEN_WITH_DEFAULTS.valueCompat.sortedWith(
            compareBy<OpenWithDefault> { it.scope }.thenBy { it.key }
        )
        checkedItems = savedInstanceState?.getBooleanArray(STATE_CHECKED_ITEMS)
            ?.takeIf { it.size == defaults.size }
            ?: BooleanArray(defaults.size)
        return MaterialAlertDialogBuilder(context, theme)
            .setTitle(R.string.settings_open_with_defaults_title)
            .apply {
                if (defaults.isEmpty()) {
                    setMessage(R.string.settings_open_with_defaults_empty)
                    setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                } else {
                    val items = defaults.map { it.getDisplayText(context) }
                        .toTypedArray<CharSequence>()
                    setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                        updateRemoveButton()
                    }
                    setPositiveButton(R.string.settings_open_with_defaults_remove) { _, _ ->
                        OpenWithDefaults.remove(defaults.filterIndexed { index, _ ->
                            checkedItems[index]
                        })
                        finish()
                    }
                    setNeutralButton(R.string.settings_open_with_defaults_remove_all) { _, _ ->
                        OpenWithDefaults.remove(defaults)
                        finish()
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                }
            }
            .create()
            .apply { setOnShowListener { updateRemoveButton() } }
    }

    private fun updateRemoveButton() {
        val dialog = dialog as AlertDialog? ?: return
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
            defaults.isEmpty() || checkedItems.any { it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBooleanArray(STATE_CHECKED_ITEMS, checkedItems)
    }

    // Not onDismiss(), which is also called when the dialog is recreated for a configuration
    // change.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    private fun OpenWithDefault.getDisplayText(context: Context): String {
        val target = when (scope) {
            OpenWithDefault.Scope.FILE -> {
                val uri = try {
                    URI(key)
                } catch (e: Exception) {
                    null
                }
                if (uri?.scheme == "file") uri.path else key
            }
            OpenWithDefault.Scope.EXTENSION ->
                if (key.isEmpty()) {
                    context.getString(R.string.settings_open_with_defaults_no_extension)
                } else {
                    context.getString(R.string.file_open_with_scope_extension_format, key)
                }
        }
        val app = component.getOpenWithLabel(context)
            ?: context.getString(R.string.file_open_with_app_not_installed)
        return context.getString(R.string.settings_open_with_defaults_item_format, target, app)
    }

    companion object {
        private const val STATE_CHECKED_ITEMS = "checked_items"
    }
}
