/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity

class OpenWithDefaultsDialogActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(
                    OpenWithDefaultsDialogFragment(),
                    OpenWithDefaultsDialogFragment::class.java.name
                )
            }
        }
    }
}
