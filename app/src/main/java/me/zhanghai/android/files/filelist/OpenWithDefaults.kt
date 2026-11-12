/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.filelist

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromExtension
import me.zhanghai.android.files.file.intentType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.valueCompat

/**
 * An app the user chose to always open a file, or all files with an extension, with.
 *
 * These are kept by the app instead of the system, because the system's "Always" choices are hard
 * to find and reset, and a choice for one MIME type can also capture files of unknown type (which
 * are opened as any type).
 */
@Parcelize
data class OpenWithDefault(
    val scope: Scope,
    /**
     * The file's URI for [Scope.FILE], or the lowercase file extension (empty for files without an
     * extension) for [Scope.EXTENSION].
     */
    val key: String,
    val component: ComponentName,
    /** The MIME type to open the file as, which may differ from the guessed one. */
    val mimeType: String
) : Parcelable {
    enum class Scope {
        FILE,
        EXTENSION
    }
}

val Path.openWithFileKey: String
    get() = toUri().toString()

val Path.openWithExtensionKey: String
    get() = name.asFileNameOrNull()?.singleExtension?.lowercase() ?: ""

fun Path.getOpenWithKey(scope: OpenWithDefault.Scope): String =
    when (scope) {
        OpenWithDefault.Scope.FILE -> openWithFileKey
        OpenWithDefault.Scope.EXTENSION -> openWithExtensionKey
    }

/** Describes what a default applies to, e.g. "this file" or ".zshrc files". */
fun OpenWithDefault.Scope.getDescription(extensionKey: String, context: Context): String =
    when {
        this == OpenWithDefault.Scope.FILE -> context.getString(R.string.file_open_with_scope_file)
        extensionKey.isEmpty() -> context.getString(R.string.file_open_with_scope_no_extension)
        else -> context.getString(R.string.file_open_with_scope_extension_format, extensionKey)
    }

/**
 * Returns the activity's label, followed by its app's name if that's different, or null if the
 * activity isn't installed.
 */
fun ComponentName.getOpenWithLabel(context: Context): String? {
    val packageManager = context.packageManager
    val activityInfo = try {
        @Suppress("DEPRECATION")
        packageManager.getActivityInfo(this, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        return null
    }
    val label = activityInfo.loadLabel(packageManager).toString()
    val appLabel = activityInfo.applicationInfo.loadLabel(packageManager).toString()
    return if (label == appLabel) {
        label
    } else {
        context.getString(R.string.file_open_with_app_label_format, label, appLabel)
    }
}

fun Intent.withOpenWithTarget(component: ComponentName, mimeType: String): Intent =
    Intent(this)
        // Calling setType() will clear data.
        .setDataAndType(data, mimeType)
        .setComponent(component)

object OpenWithDefaults {
    /**
     * Finds the default for a file.
     *
     * @param intentType The file's own intent MIME type, if known. A file whose type isn't the one
     * its name implies (e.g. a document from a provider) isn't covered by its extension's default.
     */
    fun find(path: Path, intentType: String? = null): OpenWithDefault? {
        val defaults = Settings.OPEN_WITH_DEFAULTS.valueCompat
        val fileKey = path.openWithFileKey
        defaults.find { it.scope == OpenWithDefault.Scope.FILE && it.key == fileKey }
            ?.let { return it }
        val extensionKey = path.openWithExtensionKey
        if (intentType != null &&
            intentType != MimeType.guessFromExtension(extensionKey).intentType) {
            return null
        }
        return defaults.find {
            it.scope == OpenWithDefault.Scope.EXTENSION && it.key == extensionKey
        }
    }

    fun set(path: Path, scope: OpenWithDefault.Scope, component: ComponentName, mimeType: String) {
        val default = OpenWithDefault(scope, path.getOpenWithKey(scope), component, mimeType)
        val fileKey = path.openWithFileKey
        Settings.OPEN_WITH_DEFAULTS.putValue(
            Settings.OPEN_WITH_DEFAULTS.valueCompat.filterNot {
                (it.scope == default.scope && it.key == default.key) ||
                    // A default for this file would otherwise keep overriding the new choice.
                    (it.scope == OpenWithDefault.Scope.FILE && it.key == fileKey)
            } + default
        )
    }

    fun remove(defaults: Collection<OpenWithDefault>) {
        Settings.OPEN_WITH_DEFAULTS.putValue(
            Settings.OPEN_WITH_DEFAULTS.valueCompat.filterNot { it in defaults }
        )
    }

    /**
     * Opens [path] with its default app, if there is one. A default whose app can no longer be
     * started is removed.
     *
     * @return Whether the file was opened.
     */
    fun open(path: Path, intent: Intent, context: Context): Boolean {
        val default = find(path, intent.type) ?: return false
        return try {
            context.startActivity(intent.withOpenWithTarget(default.component, default.mimeType))
            true
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            remove(listOf(default))
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            remove(listOf(default))
            false
        }
    }
}
