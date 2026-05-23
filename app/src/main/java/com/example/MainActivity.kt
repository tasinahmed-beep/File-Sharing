package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.GroupDropApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GroupDropViewModel
import com.example.ui.viewmodel.GroupDropViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val factory = GroupDropViewModelFactory(applicationContext)
    val viewModel = ViewModelProvider(this, factory)[GroupDropViewModel::class.java]
    
    setContent {
      val lightMode by viewModel.lightMode.collectAsState()
      val isDarkTheme = !lightMode

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          GroupDropApp(viewModel)
        }
      }
    }
  }
}
