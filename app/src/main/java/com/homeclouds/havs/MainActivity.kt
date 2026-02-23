package com.homeclouds.havs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.homeclouds.havs.ui.AppNav
import com.homeclouds.havs.ui.theme.HAVSCalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HAVSCalculatorTheme {
                AppNav()
            }
        }
    }
}
