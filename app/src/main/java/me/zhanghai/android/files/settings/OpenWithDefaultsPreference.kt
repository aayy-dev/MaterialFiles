/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.lifecycle.Observer
import androidx.preference.Preference
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.OpenWithDefault
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

class OpenWithDefaultsPreference : Preference {
    private val emptySummary = summary

    private val observer = Observer<List<OpenWithDefault>> { onDefaultsChanged(it) }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()

        Settings.OPEN_WITH_DEFAULTS.observeForever(observer)
    }

    override fun onDetached() {
        super.onDetached()

        Settings.OPEN_WITH_DEFAULTS.removeObserver(observer)
    }

    private fun onDefaultsChanged(defaults: List<OpenWithDefault>) {
        summary = if (defaults.isNotEmpty()) {
            context.resources.getQuantityString(
                R.plurals.settings_open_with_defaults_summary_format, defaults.size, defaults.size
            )
        } else {
            emptySummary
        }
    }

    override fun onClick() {
        context.startActivitySafe(OpenWithDefaultsDialogActivity::class.createIntent())
    }
}
