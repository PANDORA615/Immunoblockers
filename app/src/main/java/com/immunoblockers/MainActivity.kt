package com.immunoblockers

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immunoblockers.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var requestedDose by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedDose = intent.getStringExtra("doseId")
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            AppTheme(settings) { AppRoot(vm, requestedDose) { requestedDose = null } }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); requestedDose = intent.getStringExtra("doseId") }
    override fun onResume() { super.onResume(); vm.refresh() }
}
