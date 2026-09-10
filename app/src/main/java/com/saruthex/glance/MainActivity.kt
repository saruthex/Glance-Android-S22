package com.saruthex.glance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MainActivity : ComponentActivity() {
    private var cameraGranted by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        setContent { GlanceApp(cameraGranted) }
    }
}

@Composable
fun GlanceApp(cameraGranted: Boolean) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AB4FF), surface = Color(0xFF10131A), background = Color(0xFF090B10))) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(containerColor = Color(0xFF090B10), bottomBar = {
            NavigationBar(containerColor = Color(0xFF10131A)) {
                listOf("Glance", "Enroll", "Faces", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Text(if (tab == index) "◉" else "○") }, label = { Text(label) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(cameraGranted)
                    1 -> EnrollmentScreen(cameraGranted)
                    2 -> FacesScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(cameraGranted: Boolean) {
    var scanning by remember { mutableStateOf(false) }
    var faceCount by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Text("GLANCE", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8AB4FF))
        Text("Face recognition", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        if (scanning && cameraGranted) {
            CameraFacePreview(Modifier.size(310.dp).clip(CircleShape)) { faceCount = it.size }
        } else {
            Surface(Modifier.size(310.dp), shape = CircleShape, color = Color(0xFF151922)) {
                Box(contentAlignment = Alignment.Center) { Text(if (cameraGranted) "Ready to scan" else "Camera permission required") }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(when {
            !cameraGranted -> "Allow camera access to continue."
            !scanning -> "Tap Start recognition to use the front camera."
            faceCount == 0 -> "Looking for a face…"
            faceCount == 1 -> "Face detected • recognition model coming next"
            else -> "$faceCount faces detected • use one face at a time"
        }, color = Color.LightGray)
        Spacer(Modifier.weight(1f))
        Button(onClick = { scanning = !scanning }, enabled = cameraGranted, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text(if (scanning) "Stop camera" else "Start recognition")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EnrollmentScreen(cameraGranted: Boolean) {
    var pose by remember { mutableIntStateOf(0) }
    var faceDetected by remember { mutableStateOf(false) }
    val poses = listOf("Look straight ahead", "Turn slightly left", "Turn slightly right", "Look up", "Look down")
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("Enroll your face", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Step ${pose + 1} of ${poses.size}", color = Color(0xFF8AB4FF))
        Spacer(Modifier.height(20.dp))
        if (cameraGranted) CameraFacePreview(Modifier.size(300.dp).clip(CircleShape)) { faceDetected = it.isNotEmpty() }
        else Text("Camera permission required")
        Spacer(Modifier.height(24.dp))
        Text(poses[pose], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (faceDetected) "Face detected ✓" else "Position your face inside the camera", color = Color.LightGray)
        Spacer(Modifier.weight(1f))
        Button(onClick = { if (faceDetected && pose < poses.lastIndex) pose++ },
            enabled = faceDetected && pose < poses.lastIndex, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (pose == poses.lastIndex) "Enrollment model coming next" else "Capture this pose")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun FacesScreen() = CenterPage("Saved identities", "No identity has been enrolled yet. The next version will store encrypted face embeddings locally.")
@Composable private fun SettingsScreen() = CenterPage("Settings", "Camera and detection are now active. Recognition, liveness and privacy controls are the next phase.")
@Composable private fun CenterPage(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text(subtitle, color = Color.LightGray)
    }
}

@Composable
private fun CameraFacePreview(modifier: Modifier = Modifier, onFacesChanged: (List<Face>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .enableTracking().build())
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) proxy.close() else {
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { onFacesChanged(it) }
                        .addOnFailureListener { onFacesChanged(emptyList()) }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (_: Exception) { onFacesChanged(emptyList()) }
        }, executor)
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier.background(Color(0xFF151922)))
}
