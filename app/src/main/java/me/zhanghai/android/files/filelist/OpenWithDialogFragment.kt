/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.OpenWithDialogBinding
import me.zhanghai.android.files.databinding.OpenWithItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import java.text.Collator

/**
 * Lets the user pick the app to open a file with, and optionally remember it for the file or for
 * all files with its extension.
 */
class OpenWithDialogFragment : AppCompatDialogFragment() {
    // Read from the host activity instead of being copied into fragment arguments, because the
    // intent can carry a long list of image paths for the image viewer.
    private val args: Args
        get() = (requireActivity() as OpenWithDialogActivity).args

    private var isItemClicked = false

    private lateinit var binding: OpenWithDialogBinding

    private lateinit var adapter: OpenWithAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(getString(R.string.file_open_with_title_format, args.path.name))
            .apply {
                binding = OpenWithDialogBinding.inflate(context.layoutInflater)
                val extensionKey = args.path.openWithExtensionKey
                binding.rememberGroup.isVisible =
                    args.canRememberForFile || args.canRememberForExtension
                binding.fileRadio.isVisible = args.canRememberForFile
                binding.extensionRadio.isVisible = args.canRememberForExtension
                if (resources.configuration.screenHeightDp < SHORT_SCREEN_HEIGHT_DP) {
                    // Leave room for the list, e.g. on a phone in landscape.
                    binding.rememberGroup.orientation = LinearLayout.HORIZONTAL
                    binding.rememberGroup.children.forEach {
                        it.updateLayoutParams<RadioGroup.LayoutParams> {
                            width = 0
                            weight = 1f
                        }
                    }
                }
                binding.fileRadio.text = getString(
                    R.string.file_open_with_remember_always_format,
                    OpenWithDefault.Scope.FILE.getDescription(extensionKey, context)
                )
                binding.extensionRadio.text = getString(
                    R.string.file_open_with_remember_always_format,
                    OpenWithDefault.Scope.EXTENSION.getDescription(extensionKey, context)
                )
                binding.clearDefaultButton.setOnClickListener { clearDefault() }
                binding.recyclerView.layoutManager = LinearLayoutManager(context)
                adapter = OpenWithAdapter { onItemClicked(it) }
                binding.recyclerView.adapter = adapter
                setView(binding.root)
                updateDefault()
                loadItems()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .create()

    private fun updateDefault() {
        val context = requireContext()
        val default = OpenWithDefaults.find(args.path)
        binding.defaultLayout.isVisible = default != null
        if (default != null) {
            val extensionKey = if (default.scope == OpenWithDefault.Scope.EXTENSION) {
                default.key
            } else {
                ""
            }
            binding.defaultText.text = getString(
                R.string.file_open_with_default_format,
                default.scope.getDescription(extensionKey, context),
                default.component.getOpenWithLabel(context)
                    ?: getString(R.string.file_open_with_app_not_installed)
            )
        }
        adapter.defaultComponent = default?.component
    }

    private fun clearDefault() {
        OpenWithDefaults.find(args.path)?.let { OpenWithDefaults.remove(listOf(it)) }
        updateDefault()
    }

    private fun loadItems() {
        val context = requireContext().applicationContext
        val intent = args.intent
        val showAllApps = args.showAllApps
        lifecycleScope.launch {
            val items = withContext(Dispatchers.Default) {
                createItems(intent, showAllApps, context)
            }
            binding.progress.isVisible = false
            adapter.replace(items)
        }
    }

    private fun onItemClicked(item: OpenWithItem) {
        if (isItemClicked) {
            return
        }
        isItemClicked = true
        when (item.type) {
            OpenWithItem.Type.APP -> {
                val component = item.component!!
                val scope = when (binding.rememberGroup.checkedRadioButtonId) {
                    R.id.fileRadio -> OpenWithDefault.Scope.FILE
                    R.id.extensionRadio -> OpenWithDefault.Scope.EXTENSION
                    else -> null
                }
                // Only remember an app once it has been started successfully.
                if (startActivityOrShowError(Intent(args.intent).setComponent(component)) &&
                    scope != null) {
                    OpenWithDefaults.set(
                        args.path, scope, component, args.intent.type ?: MimeType.ANY.value
                    )
                }
            }
            OpenWithItem.Type.OPEN_AS ->
                startActivitySafe(
                    OpenFileAsDialogActivity::class.createIntent()
                        .putArgs(
                            OpenFileAsDialogFragment.Args(
                                args.path, args.canRememberForFile, args.canRememberForExtension
                            )
                        )
                )
            OpenWithItem.Type.EDIT ->
                // Use the system chooser so that an "Always" choice can't be made for editing.
                startActivitySafe(args.intent.toEditIntent().withChooser())
        }
        finish()
    }

    private fun startActivityOrShowError(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            showToast(R.string.activity_not_found)
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            showToast(R.string.activity_not_found)
            false
        }

    // Not onDismiss(), which is also called when the dialog is recreated for a configuration
    // change.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        /** The view intent for the file, including its MIME type and any extras. */
        val intent: Intent,
        /** Whether a default can be remembered for this exact file, e.g. not for a temp copy. */
        val canRememberForFile: Boolean,
        /**
         * Whether a default can be remembered for the file's extension, e.g. not for files that
         * are opened in this app instead, like APKs and archives.
         */
        val canRememberForExtension: Boolean,
        /** Whether to ask even if there's a default. */
        val alwaysAsk: Boolean,
        /** Whether to list every app that matches the intent, even for files of unknown type. */
        val showAllApps: Boolean
    ) : ParcelableArgs

    companion object {
        private fun createItems(
            intent: Intent,
            showAllApps: Boolean,
            context: Context
        ): List<OpenWithItem> {
            val packageManager = context.packageManager
            val queryIntent = Intent(intent).setComponent(null)
                .apply { replaceExtras(null as Bundle?) }
            // Files of unknown type are opened as any type, which matches every app that accepts
            // some type, e.g. calendars and contacts. Unless asked for every app, only list the
            // apps that accept generic files, plus the text editor.
            val isUnknownType = !showAllApps && intent.type == MimeType.ANY.value
            if (isUnknownType) {
                // Calling setType() will clear data.
                queryIntent.setDataAndType(intent.data, MimeType.GENERIC.value)
            }
            val resolveInfos = try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            val apps = resolveInfos.filter { it.activityInfo.canBeStartedBy(context) }.mapTo(
                mutableListOf()
            ) {
                AppInfo(
                    it.activityInfo, it.loadLabel(packageManager).toString(),
                    it.loadIcon(packageManager), packageManager
                )
            }
            if (isUnknownType) {
                val textEditor = ComponentName(context, TextEditorActivity::class.java)
                if (apps.none { it.component == textEditor }) {
                    @Suppress("DEPRECATION")
                    val activityInfo = packageManager.getActivityInfo(textEditor, 0)
                    apps += AppInfo(
                        activityInfo, activityInfo.loadLabel(packageManager).toString(),
                        activityInfo.loadIcon(packageManager), packageManager
                    )
                }
            }
            // Tell apart activities of the same app that share a label.
            val duplicateLabels = apps.groupBy { it.component.packageName to it.label }
                .filterValues { it.size > 1 }
                .keys
            val collator = Collator.getInstance()
            val appItems = apps
                .sortedWith(
                    compareBy<AppInfo> { it.component.packageName != context.packageName }
                        .thenComparator { app1, app2 -> collator.compare(app1.label, app2.label) }
                )
                .map {
                    val subtitle = when {
                        (it.component.packageName to it.label) in duplicateLabels ->
                            it.component.className.substringAfterLast('.')
                        it.label != it.appLabel -> it.appLabel
                        else -> null
                    }
                    OpenWithItem(
                        OpenWithItem.Type.APP, it.component, it.icon, 0, it.label, subtitle
                    )
                }
            val otherItems = buildList {
                add(
                    OpenWithItem(
                        OpenWithItem.Type.OPEN_AS, null, null, R.drawable.open_as_icon,
                        context.getString(R.string.file_open_as_title), null
                    )
                )
                val canEdit = try {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(
                        intent.toEditIntent(), PackageManager.MATCH_DEFAULT_ONLY
                    ).isNotEmpty()
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (canEdit) {
                    add(
                        OpenWithItem(
                            OpenWithItem.Type.EDIT, null, null, R.drawable.edit_icon,
                            context.getString(R.string.file_open_with_edit), null
                        )
                    )
                }
            }
            return appItems + otherItems
        }

        /**
         * Whether another app's activity can be started by this app; the system resolver leaves
         * out the ones that can't.
         */
        private fun ActivityInfo.canBeStartedBy(context: Context): Boolean =
            packageName == context.packageName || (
                exported && (
                    permission == null ||
                        context.checkSelfPermission(permission) ==
                        PackageManager.PERMISSION_GRANTED
                    )
                )

        private const val SHORT_SCREEN_HEIGHT_DP = 480

        private fun Intent.toEditIntent(): Intent =
            Intent(Intent.ACTION_EDIT)
                // Calling setType() will clear data.
                .setDataAndType(data, type)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
    }

    private class AppInfo(
        activityInfo: ActivityInfo,
        val label: String,
        val icon: Drawable,
        packageManager: PackageManager
    ) {
        val component = ComponentName(activityInfo.packageName, activityInfo.name)
        val appLabel = activityInfo.applicationInfo.loadLabel(packageManager).toString()
    }
}

/**
 * Opens a file with its remembered app, or lets the user choose one.
 *
 * @param intent The view intent for the file.
 * @param alwaysAsk Whether to let the user choose even if there's a remembered app.
 * @param canRememberForFile Whether a choice can be remembered for this exact file.
 * @param canRememberForExtension Whether a choice can be remembered for the file's extension.
 * @param showAllApps Whether to list every app that matches the intent's MIME type, even when it's
 * any type.
 */
fun Context.openFileWithApp(
    path: Path,
    intent: Intent,
    alwaysAsk: Boolean,
    canRememberForFile: Boolean = true,
    canRememberForExtension: Boolean = true,
    showAllApps: Boolean = false
) {
    if (!alwaysAsk && OpenWithDefaults.open(path, intent, this)) {
        return
    }
    startActivitySafe(
        createOpenFileWithAppIntent(
            path, intent, true, canRememberForFile, canRememberForExtension, showAllApps
        )
    )
}

/** Creates an intent that does what [openFileWithApp] does, e.g. for starting from a service. */
fun createOpenFileWithAppIntent(
    path: Path,
    intent: Intent,
    alwaysAsk: Boolean,
    canRememberForFile: Boolean = true,
    canRememberForExtension: Boolean = true,
    showAllApps: Boolean = false
): Intent =
    OpenWithDialogActivity::class.createIntent()
        .putArgs(
            OpenWithDialogFragment.Args(
                path, intent, canRememberForFile, canRememberForExtension, alwaysAsk, showAllApps
            )
        )

private data class OpenWithItem(
    val type: Type,
    val component: ComponentName?,
    val icon: Drawable?,
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String?
) {
    enum class Type {
        APP,
        OPEN_AS,
        EDIT
    }
}

private class OpenWithAdapter(
    private val onItemClick: (OpenWithItem) -> Unit
) : SimpleAdapter<OpenWithItem, OpenWithAdapter.ViewHolder>() {
    var defaultComponent: ComponentName? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyDataSetChanged()
        }

    override val hasStableIds: Boolean
        get() = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(OpenWithItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        binding.itemLayout.setOnClickListener { onItemClick(item) }
        if (item.icon != null) {
            binding.iconImage.setImageDrawable(item.icon)
        } else {
            binding.iconImage.setImageResource(item.iconRes)
        }
        binding.titleText.text = item.title
        val isDefault = item.component != null && item.component == defaultComponent
        binding.subtitleText.text = listOfNotNull(
            item.subtitle,
            if (isDefault) {
                binding.subtitleText.context.getString(R.string.file_open_with_default_badge)
            } else {
                null
            }
        ).joinToString(" · ")
    }

    class ViewHolder(val binding: OpenWithItemBinding) : RecyclerView.ViewHolder(binding.root)
}
