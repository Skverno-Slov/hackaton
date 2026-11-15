package com.nepalindrom.fielddispatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nepalindrom.fielddispatch.ui.main.MainScreen
import com.nepalindrom.fielddispatch.ui.theme.FieldDispatchTheme
import com.nepalindrom.fielddispatch.util.AppPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            FieldDispatchTheme {
                MainScreen()
            }
        }
    }
}
