package com.zwz.offlineledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zwz.offlineledger.ui.LedgerApp
import com.zwz.offlineledger.ui.theme.LedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedgerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LedgerApp()
                }
            }
        }
    }
}
