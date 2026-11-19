package com.momentoestoico

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.ui.MainViewModel
import com.momentoestoico.ui.ZenApp
import com.momentoestoico.ui.theme.ZenTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            ZenTheme { ZenApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Al tocar la notificación se abre el momento con la respiración guiada. */
    private fun handle(intent: Intent?) {
        val thought = StoicLibrary.get(intent?.getStringExtra(EXTRA_THOUGHT_ID)) ?: return
        intent?.removeExtra(EXTRA_THOUGHT_ID)
        vm.openMoment(thought, breathe = true)
    }

    companion object {
        const val EXTRA_THOUGHT_ID = "thought_id"
    }
}
