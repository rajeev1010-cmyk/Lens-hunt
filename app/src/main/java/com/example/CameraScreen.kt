package com.example

import android.Manifest
import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// Giant Hunt brand palette, sampled from selfie_screen.png (gold-on-black HUD).
private val GiantHuntGold = Color(0xFFFFA800)
private val GiantHuntGoldDim = Color(0xFFC9821A)
private val GiantHuntBlack = Color(0xFF000000)
private val GiantHuntCard = Color(0xFF141414)

// Fractional bounds of the transparent viewfinder cutout in selfie_screen.png
// (941x1672 source), used to align the live camera feed with the frame art.
private const val CutoutLeftFrac = 74f / 941f
private const val CutoutRightFrac = 864f / 941f
private const val CutoutTopFrac = 456f / 1672f
private const val CutoutBottomFrac = 1307f / 1672f

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val matchError by viewModel.matchError.collectAsState()
    val matchedCharacter by viewModel.matchedCharacter.collectAsState()
    val matchSimilarity by viewModel.matchSimilarity.collectAsState()
    val context = LocalContext.current

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                viewModel.matchPhoto(bitmap)
            } else {
                Toast.makeText(context, "Couldn't load that photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(matchError) {
        matchError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMatchError()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GiantHuntBlack)
    ) {
        val cutoutLeft = maxWidth * CutoutLeftFrac
        val cutoutTop = maxHeight * CutoutTopFrac
        val cutoutWidth = maxWidth * (CutoutRightFrac - CutoutLeftFrac)
        val cutoutHeight = maxHeight * (CutoutBottomFrac - CutoutTopFrac)

        Box(
            modifier = Modifier
                .offset(x = cutoutLeft, y = cutoutTop)
                .size(cutoutWidth, cutoutHeight)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreviewContent(viewModel, lensFacing)
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission required", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GiantHuntGold,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        Image(
            painter = painterResource(id = R.drawable.selfie_screen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Camera flip button, floating over the top-right of the viewfinder cutout.
        Box(
            modifier = Modifier
                .offset(x = cutoutLeft + cutoutWidth - 48.dp, y = cutoutTop + 8.dp)
                .size(40.dp)
                .background(GiantHuntBlack.copy(alpha = 0.6f), CircleShape)
                .border(1.dp, GiantHuntGold.copy(alpha = 0.6f), CircleShape)
                .clip(CircleShape)
                .clickable {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FlipCameraAndroid, contentDescription = "Switch camera", tint = GiantHuntGold)
        }

        // Match result card, floating just below the viewfinder cutout.
        if (matchedCharacter != null) {
            Box(
                modifier = Modifier
                    .offset(x = cutoutLeft, y = cutoutTop + cutoutHeight + 12.dp)
                    .width(cutoutWidth)
                    .background(GiantHuntCard, RoundedCornerShape(20.dp))
                    .border(1.dp, GiantHuntGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(GiantHuntGold, GiantHuntGoldDim)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = matchedCharacter!!.name.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2).joinToString("").uppercase()
                        Text(initials, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "MATCH" + (matchSimilarity?.let { " · $it%" } ?: ""),
                            color = GiantHuntGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            matchedCharacter!!.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            matchedCharacter!!.series,
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Floating upload button.
        FloatingActionButton(
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            containerColor = GiantHuntGold,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Upload photo")
        }
    }
}

@Composable
fun CameraPreviewContent(viewModel: MainViewModel, lensFacing: Int) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val faceBounds by viewModel.faceBounds.collectAsState()

    var imageWidth by remember { mutableIntStateOf(1) }
    var imageHeight by remember { mutableIntStateOf(1) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    cameraExecutor,
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
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Face-tracking HUD corners.
        Canvas(modifier = Modifier.fillMaxSize()) {
            faceBounds?.let { bounds ->
                val scaleX = size.width / imageWidth
                val scaleY = size.height / imageHeight
                val scale = maxOf(scaleX, scaleY)
                val offsetX = (size.width - imageWidth * scale) / 2
                val offsetY = (size.height - imageHeight * scale) / 2

                // Only the front camera's analysis frames need a horizontal
                // flip to match the (auto-mirrored) preview the user sees.
                val mappedLeft: Float
                val mappedRight: Float
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    mappedLeft = (imageWidth - bounds.right) * scale + offsetX
                    mappedRight = (imageWidth - bounds.left) * scale + offsetX
                } else {
                    mappedLeft = bounds.left * scale + offsetX
                    mappedRight = bounds.right * scale + offsetX
                }
                val mappedTop = bounds.top * scale + offsetY
                val mappedBottom = bounds.bottom * scale + offsetY

                val strokeWidth = 3.dp.toPx()
                val cornerLength = 24.dp.toPx()
                val color = GiantHuntGold

                drawLine(color, Offset(mappedLeft, mappedTop), Offset(mappedLeft + cornerLength, mappedTop), strokeWidth)
                drawLine(color, Offset(mappedLeft, mappedTop), Offset(mappedLeft, mappedTop + cornerLength), strokeWidth)

                drawLine(color, Offset(mappedRight, mappedTop), Offset(mappedRight - cornerLength, mappedTop), strokeWidth)
                drawLine(color, Offset(mappedRight, mappedTop), Offset(mappedRight, mappedTop + cornerLength), strokeWidth)

                drawLine(color, Offset(mappedLeft, mappedBottom), Offset(mappedLeft + cornerLength, mappedBottom), strokeWidth)
                drawLine(color, Offset(mappedLeft, mappedBottom), Offset(mappedLeft, mappedBottom - cornerLength), strokeWidth)

                drawLine(color, Offset(mappedRight, mappedBottom), Offset(mappedRight - cornerLength, mappedBottom), strokeWidth)
                drawLine(color, Offset(mappedRight, mappedBottom), Offset(mappedRight, mappedBottom - cornerLength), strokeWidth)
            }
        }
    }
}
