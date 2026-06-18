package com.example.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.petapp.di.LocalViewModelFactory
import com.example.petapp.ui.MainViewModel
import com.example.petapp.ui.screens.ChatScreen
import com.example.petapp.ui.screens.ContextSettingsScreen
import com.example.petapp.ui.screens.MemoryLayersScreen
import com.example.petapp.ui.screens.ProfilesScreen
import com.example.petapp.ui.theme.PetAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = (application as App).appComponent.viewModelFactory()
        setContent {
            PetAppTheme {
                CompositionLocalProvider(LocalViewModelFactory provides factory) {
                    val navController = rememberNavController()
                    val chatViewModel: MainViewModel = viewModel(factory = factory)

                    NavHost(navController = navController, startDestination = "chat") {
                        composable("chat") {
                            ChatScreen(navController = navController, viewModel = chatViewModel)
                        }
                        composable("context_settings") {
                            ContextSettingsScreen(
                                navController = navController,
                                chatViewModel = chatViewModel
                            )
                        }
                        composable("memory_layers") {
                            MemoryLayersScreen(
                                navController = navController,
                                viewModel = chatViewModel
                            )
                        }
                        composable("profiles") {
                            ProfilesScreen(
                                navController = navController,
                                viewModel = chatViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
