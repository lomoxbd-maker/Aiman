package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MAXAppShell
import com.example.viewmodel.MAXViewModel

class MainActivity : ComponentActivity() {

  private val maxViewModel: MAXViewModel by viewModels {
    val app = application as MAXApplication
    MAXViewModel.Factory(
      repository = app.repository,
      maxCore = app.maxCore,
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MAXAppShell(viewModel = maxViewModel)
    }
  }
}
