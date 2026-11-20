/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import com.jay.m3play.utils.CrashGuard

/**
 * Shown in its own process (":crash") after the app dies, so it works even when MainActivity or
 * Compose is what crashed. Plain Views only: no Compose, no Hilt, no theme dependencies.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.init(this)
        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "No crash information was received."
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(pad, pad * 3, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "M3 Play crashed"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        val glassNote = if (!CrashGuard.glassEnabled) {
            "SeaGlass was turned off automatically after repeated startup crashes."
        } else {
            "Tap Copy and send this text so the bug can be fixed."
        }
        root.addView(TextView(this).apply {
            text = glassNote
            textSize = 14f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, pad / 2, 0, pad / 2)
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2128"))
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        scroll.addView(TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E0E6EB"))
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, 0)
        }
        buttons.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("M3 Play crash", trace))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        })
        val files = intent.getStringArrayExtra(EXTRA_FILES)?.map { File(it) }?.filter { it.exists() }.orEmpty()
        if (files.isNotEmpty()) {
            buttons.addView(Button(this).apply {
                text = "Share report"
                setOnClickListener {
                    runCatching {
                        val uris = ArrayList(files.map { FileProvider.getUriForFile(this@CrashActivity, "$packageName.fileprovider", it) })
                        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            putExtra(Intent.EXTRA_SUBJECT, "M3 Play crash report")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Share crash report"))
                    }.onFailure {
                        Toast.makeText(this@CrashActivity, "Could not share: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
        buttons.addView(Button(this).apply {
            text = "Restart"
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(launch)
                }
                finish()
            }
        })
        root.addView(buttons)
        setContentView(root)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
        const val EXTRA_FILES = "files"
    }
}
