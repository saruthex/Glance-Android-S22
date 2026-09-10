package com.saruthex.glance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var cameraGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)

        setContent { GlanceApp(cameraGranted) }
    }
}

@Composable
fun GlanceApp(cameraGranted: Boolean) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4FF),
            surface = Color(0xFF090B10),
            background = Color(0xFF090B10)
        )
    ) {
        var tab by remember { mutableStateOf(0) }
        Scaffold(
            containerColor = Color(0xFF090B10),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF10131A)) {
                    listOf("Glance", "Enroll", "Faces", "Settings").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(if (index == 0) "◉" else "○") },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(cameraGranted)
                    1 -> EnrollmentScreen()
                    2 -> FacesScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(cameraGranted: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Text("GLANCE", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8AB4FF))
        Text("Face recognition", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.size(270.dp),
            shape = CircleShape,
            color = Color(0xFF151922)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (cameraGranted) "Ready to scan" else "Camera permission required")
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Your face data stays on this device.", color = Color.LightGray)
        Spacer(Modifier.weight(1f))
        Button(onClick = {}) { Text("Start recognition") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun EnrollmentScreen() = CenterPage("Enroll your face", "Guided multi-pose enrollment is the next pipeline step.")
@Composable private fun FacesScreen() = CenterPage("Saved identities", "Encrypted local face templates will appear here.")
@Composable private fun SettingsScreen() = CenterPage("Settings", "Recognition, liveness and privacy controls.")

@Composable
private fun CenterPage(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = Color.LightGray)
    }
}
