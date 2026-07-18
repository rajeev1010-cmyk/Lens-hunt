package com.example

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
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
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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
    val uiStage by viewModel.uiStage.collectAsState()
    val topMatches by viewModel.topMatches.collectAsState()
    val selectedMatch by viewModel.selectedMatch.collectAsState()
    val capturedSelfie by viewModel.capturedSelfie.collectAsState()
    val context = LocalContext.current

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

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
                CameraPreviewContent(
                    viewModel = viewModel,
                    lensFacing = lensFacing,
                    onImageCaptureReady = { imageCaptureRef = it }
                )
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

        // Shutter button, at the bottom-curve of the viewfinder frame.
        val shutterInteractionSource = remember { MutableInteractionSource() }
        val isShutterPressed by shutterInteractionSource.collectIsPressedAsState()
        val shutterScale by animateFloatAsState(if (isShutterPressed) 0.85f else 1f, label = "shutterScale")

        Box(
            modifier = Modifier
                .offset(x = cutoutLeft + cutoutWidth / 2 - 34.dp, y = cutoutTop + cutoutHeight - 34.dp)
                .size(68.dp)
                .scale(shutterScale)
                .background(GiantHuntBlack, CircleShape)
                .border(3.dp, GiantHuntGold, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = shutterInteractionSource,
                    indication = LocalIndication.current
                ) {
                    val capture = imageCaptureRef
                    if (capture == null) {
                        Toast.makeText(context, "Camera not ready yet.", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val raw = image.toBitmap()
                                image.close()
                                val bitmap = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    raw.mirroredHorizontally()
                                } else {
                                    raw
                                }
                                viewModel.onSelfieCaptured(bitmap)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Toast.makeText(context, "Couldn't capture photo.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(GiantHuntGold, CircleShape)
            )
        }

        if (uiStage == UiStage.TOP_MATCHES) {
            TopMatchesOverlay(
                matches = topMatches,
                onSelect = { viewModel.selectMatch(it) },
                onDismiss = { viewModel.dismissOverlay() }
            )
        }

        if (uiStage == UiStage.SHARE_CARD && selectedMatch != null) {
            ShareCardOverlay(
                character = selectedMatch!!.character,
                selfie = capturedSelfie,
                onDismiss = { viewModel.dismissOverlay() }
            )
        }
    }
}

@Composable
private fun TopMatchesOverlay(
    matches: List<MatchResult>,
    onSelect: (MatchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(GiantHuntCard, RoundedCornerShape(24.dp))
                .border(1.dp, GiantHuntGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text("TOP 5 ANIME TWINS", color = GiantHuntGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            matches.forEach { match ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(match) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(GiantHuntGold, GiantHuntGoldDim)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = match.character.name.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2).joinToString("").uppercase()
                        Text(initials, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(match.character.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(match.character.series, color = Color(0xFFAAAAAA), fontSize = 11.sp, maxLines = 1)
                        if (match.character.designer.isNotBlank()) {
                            Text(match.character.designer, color = GiantHuntGoldDim, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Text("${match.similarityPercent}%", color = GiantHuntGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel", color = Color(0xFFAAAAAA))
            }
        }
    }
}

@Composable
private fun ShareCardOverlay(
    character: CharacterEntry,
    selfie: Bitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var shareCardBitmap by remember(character.id, selfie) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(character.id, selfie) {
        shareCardBitmap = withContext(Dispatchers.Default) {
            ShareCardRenderer.render(context, character, selfie)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = shareCardBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Share card",
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(20.dp))
                Row {
                    Button(
                        onClick = { shareBitmap(context, bitmap, "anime-twin-${character.id}.png") },
                        colors = ButtonDefaults.buttonColors(containerColor = GiantHuntGold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close", color = GiantHuntGold)
                    }
                }
            } else {
                CircularProgressIndicator(color = GiantHuntGold)
                Spacer(Modifier.height(12.dp))
                Text("Rendering your card…", color = Color.White)
            }
        }
    }
}

@Composable
fun CameraPreviewContent(
    viewModel: MainViewModel,
    lensFacing: Int,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val faceBounds by viewModel.faceBounds.collectAsState()
    val matchedCharacter by viewModel.matchedCharacter.collectAsState()
    val density = LocalDensity.current

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
        val imageCapture = ImageCapture.Builder().build()
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
                imageAnalyzer,
                imageCapture
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        val bounds = faceBounds
        if (bounds != null && imageWidth > 0 && imageHeight > 0) {
            val scaleX = boxWidthPx / imageWidth
            val scaleY = boxHeightPx / imageHeight
            val scale = maxOf(scaleX, scaleY)
            val offsetX = (boxWidthPx - imageWidth * scale) / 2
            val offsetY = (boxHeightPx - imageHeight * scale) / 2

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

            Canvas(modifier = Modifier.fillMaxSize()) {
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

            // Anime-twin name badge, floats with the face-tracking box.
            if (matchedCharacter != null) {
                val badgeOffsetYPx = with(density) { 28.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (mappedRight + 12).roundToInt(),
                                y = (mappedTop - badgeOffsetYPx).roundToInt().coerceAtLeast(0)
                            )
                        }
                        .background(GiantHuntGold, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        matchedCharacter!!.name,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
