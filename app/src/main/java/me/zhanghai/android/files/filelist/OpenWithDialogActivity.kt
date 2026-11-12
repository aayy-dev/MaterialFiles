/*
 * Copyright (c) 2026 aayy-dev
 */

package me.zhanghai.android.files.filelist

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs

class OpenWithDialogActivity : AppActivity() {
    private val args by args<OpenWithDialogFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            if (!args.alwaysAsk && OpenWithDefaults.open(args.path, args.intent, this)) {
                finish()
                return
            }
            val fragment = OpenWithDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, OpenWithDialogFragment::class.java.name)
            }
        }
    }
}
