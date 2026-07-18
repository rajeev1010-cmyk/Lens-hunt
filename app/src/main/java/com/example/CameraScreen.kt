package com.example

import android.Manifest
import android.content.Context
import android.graphics.Rect
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617)) // slate-950
    ) {
        TopBar()
        
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF0A0A0C))
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreviewContent(viewModel)
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission required", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Request Permission")
                    }
                }
            }
        }
        
        DataInsightSection()
        BottomNav()
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF4F46E5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Face, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("ANIME LENS AI", color = Color(0xFFF1F5F9), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = (-0.5).sp)
            Text("Real-time Scan Active", color = Color(0xFF818CF8), fontWeight = FontWeight.Medium, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun DataInsightSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InsightCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Bolt, title = "LATENCY", value = "14ms")
        InsightCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Storage, title = "DATABASE", value = "31")
        InsightCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Psychology, title = "ENGINE", value = "V4.2")
    }
}

@Composable
fun InsightCard(modifier: Modifier, icon: ImageVector, title: String, value: String) {
    Column(
        modifier = modifier
            .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFF1F5F9), fontSize = 14.sp)
    }
}

@Composable
fun BottomNav() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF020617))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RectangleShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(icon = Icons.Outlined.PhotoCamera, label = "SCAN", tint = Color(0xFF6366F1))
        NavItem(icon = Icons.Outlined.History, label = "LOG", tint = Color(0xFF64748B))
        // Capture button
        Box(
            modifier = Modifier
                .offset(y = (-20).dp)
                .size(56.dp)
                .background(Color(0xFF4F46E5), CircleShape)
                .border(4.dp, Color(0xFF020617), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(24.dp).background(Color.White, CircleShape))
        }
        NavItem(icon = Icons.Outlined.Search, label = "WIKI", tint = Color(0xFF64748B))
        NavItem(icon = Icons.Outlined.AccountCircle, label = "ME", tint = Color(0xFF64748B))
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
fun CameraPreviewContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val faceBounds by viewModel.faceBounds.collectAsState()
    val matchedCharacter by viewModel.matchedCharacter.collectAsState()
    val matchSimilarity by viewModel.matchSimilarity.collectAsState()
    val isMatching by viewModel.isMatching.collectAsState()

    var imageWidth by remember { mutableIntStateOf(1) }
    var imageHeight by remember { mutableIntStateOf(1) }
    var viewSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                FaceAnalyzer { face, imageProxy ->
                                    val bounds = face.boundingBox
                                    imageWidth = imageProxy.width
                                    imageHeight = imageProxy.height
                                    
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    if (rotation == 90 || rotation == 270) {
                                        imageWidth = imageProxy.height
                                        imageHeight = imageProxy.width
                                    }

                                    viewModel.updateFaceBounds(bounds)

                                    val axes = FaceAxesExtractor.extract(face)
                                    if (axes != null) {
                                        viewModel.matchFace(axes)
                                    }
                                    imageProxy.close()
                                }
                            )
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay for Bounding Box
        Canvas(modifier = Modifier.fillMaxSize()) {
            viewSize = size
            faceBounds?.let { bounds ->
                val scaleX = size.width / imageWidth
                val scaleY = size.height / imageHeight
                val scale = maxOf(scaleX, scaleY)
                val offsetX = (size.width - imageWidth * scale) / 2
                val offsetY = (size.height - imageHeight * scale) / 2

                val flippedLeft = imageWidth - bounds.right
                val flippedRight = imageWidth - bounds.left

                val mappedLeft = flippedLeft * scale + offsetX
                val mappedTop = bounds.top * scale + offsetY
                val mappedRight = flippedRight * scale + offsetX
                val mappedBottom = bounds.bottom * scale + offsetY

                val strokeWidth = 4.dp.toPx()
                val cornerLength = 32.dp.toPx()
                val color = Color(0xFF6366F1)

                // Top Left
                drawLine(color, start = Offset(mappedLeft, mappedTop), end = Offset(mappedLeft + cornerLength, mappedTop), strokeWidth = strokeWidth)
                drawLine(color, start = Offset(mappedLeft, mappedTop), end = Offset(mappedLeft, mappedTop + cornerLength), strokeWidth = strokeWidth)

                // Top Right
                drawLine(color, start = Offset(mappedRight, mappedTop), end = Offset(mappedRight - cornerLength, mappedTop), strokeWidth = strokeWidth)
                drawLine(color, start = Offset(mappedRight, mappedTop), end = Offset(mappedRight, mappedTop + cornerLength), strokeWidth = strokeWidth)

                // Bottom Left
                drawLine(color, start = Offset(mappedLeft, mappedBottom), end = Offset(mappedLeft + cornerLength, mappedBottom), strokeWidth = strokeWidth)
                drawLine(color, start = Offset(mappedLeft, mappedBottom), end = Offset(mappedLeft, mappedBottom - cornerLength), strokeWidth = strokeWidth)

                // Bottom Right
                drawLine(color, start = Offset(mappedRight, mappedBottom), end = Offset(mappedRight - cornerLength, mappedBottom), strokeWidth = strokeWidth)
                drawLine(color, start = Offset(mappedRight, mappedBottom), end = Offset(mappedRight, mappedBottom - cornerLength), strokeWidth = strokeWidth)
            }
        }

        // Tag UI relative to bounding box
        if (faceBounds != null && viewSize != Size.Zero) {
            val scaleX = viewSize.width / imageWidth
            val scaleY = viewSize.height / imageHeight
            val scale = maxOf(scaleX, scaleY)
            val offsetX = (viewSize.width - imageWidth * scale) / 2
            val offsetY = (viewSize.height - imageHeight * scale) / 2

            val flippedLeft = imageWidth - faceBounds!!.right
            val flippedRight = imageWidth - faceBounds!!.left
            val mappedRight = flippedRight * scale + offsetX
            val mappedTop = faceBounds!!.top * scale + offsetY

            if (isMatching || matchedCharacter != null) {
                Box(
                    modifier = Modifier
                        .offset { 
                            IntOffset(
                                x = mappedRight.roundToInt() + 16,
                                y = mappedTop.roundToInt()
                            )
                        }
                        .background(Color(0xFF4F46E5), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (matchedCharacter != null) {
                                "MATCH FOUND" + (matchSimilarity?.let { " · $it%" } ?: "")
                            } else "ANALYZING",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF4ADE80), CircleShape))
                    }
                }
            }
        }

        if (matchedCharacter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color.White, RoundedCornerShape(32.dp))
                    .border(1.dp, Color(0xFFE0E7FF), RoundedCornerShape(32.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF9333EA))), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = matchedCharacter!!.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                        Text(initials, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DETECTED CHARACTER", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(matchedCharacter!!.name, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(matchedCharacter!!.series, color = Color(0xFF4F46E5), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFEEF2FF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color(0xFF4F46E5))
                    }
                }
            }
        }
    }
}
