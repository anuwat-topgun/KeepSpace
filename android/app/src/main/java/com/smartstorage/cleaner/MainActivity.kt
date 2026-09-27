package com.smartstorage.cleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smartstorage.cleaner.ui.shell.AppShell
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SmartStorageTheme {
                AppShell()
            }
        }
    }
}
