package com.patchrelayreactdemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "Verifying React Native bundle…"; setPadding(32, 80, 32, 32) }
        setContentView(status)
        val app = application as MainApplication
        Thread {
            try {
                app.prepareBundle()
                runOnUiThread {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } catch (failure: Exception) {
                runOnUiThread { status.text = "Cannot prepare update: ${failure.message}" }
            }
        }.start()
    }
}
