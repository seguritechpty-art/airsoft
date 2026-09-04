package com.airsoft.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoft.tracker.presentation.MainViewModel
import com.airsoft.tracker.presentation.screens.LoginScreen
import com.airsoft.tracker.presentation.screens.MapScreen
import com.airsoft.tracker.presentation.theme.AirsoftTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        // Factory simple para pasar la Application a AndroidViewModel
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirsoftTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is com.airsoft.tracker.presentation.AuthState.Success -> {
            MapScreen(viewModel = viewModel, onExit = {
                viewModel.stopRealtime()
            })
        }
        else -> {
            LoginScreen(
                isLoading = authState is com.airsoft.tracker.presentation.AuthState.Loading,
                error = (authState as? com.airsoft.tracker.presentation.AuthState.Error)?.message,
                onCreateSquad = { nick -> viewModel.createSquad(nick) },
                onJoinSquad = { nick, code -> viewModel.joinSquad(nick, code) },
            )
        }
    }
}