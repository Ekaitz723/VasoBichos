package com.vaso.bichos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vaso.bichos.ui.App
import com.vaso.bichos.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel(AndroidPlatform(applicationContext)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }
}
