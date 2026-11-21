package com.cosmith.lulu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cosmith.lulu.ui.LuluRoot
import com.cosmith.lulu.ui.theme.LuluTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuluTheme {
                LuluRoot(container)
            }
        }
    }
}
