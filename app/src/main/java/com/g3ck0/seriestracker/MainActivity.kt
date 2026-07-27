package com.g3ck0.seriestracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.g3ck0.seriestracker.ui.AppRoot
import com.g3ck0.seriestracker.ui.theme.SeriesTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SeriesTrackerTheme {
                AppRoot()
            }
        }
    }
}
