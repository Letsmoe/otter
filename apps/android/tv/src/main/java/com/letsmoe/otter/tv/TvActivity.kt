package com.letsmoe.otter.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.letsmoe.otter.tv.home.HomeScreen
import com.letsmoe.otter.tv.theme.OtterTvTheme

class TvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OtterTvTheme {
                HomeScreen()
            }
        }
    }
}
