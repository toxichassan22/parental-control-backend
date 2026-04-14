package com.parentalcontrol.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.parentalcontrol.agent.ui.AgentApp
import com.parentalcontrol.agent.viewmodel.AgentViewModel

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as ParentalControlApplication).container }
    private val viewModel by viewModels<AgentViewModel> {
        AgentViewModel.factory(application, container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentApp(
                viewModel = viewModel,
                permissionStateChecker = container.permissionStateChecker,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNow()
    }
}
