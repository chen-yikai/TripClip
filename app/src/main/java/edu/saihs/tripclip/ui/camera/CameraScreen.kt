package edu.saihs.tripclip.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.components.polaroidTilt
import edu.saihs.tripclip.ui.theme.PolaroidFrame
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import edu.saihs.tripclip.ui.tripClipViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Viewfinder ratio (width / height): portrait 3:4, which is also what the sensor captures. */
private const val ViewfinderRatio = 3f / 4f

/** How long the torch stays on before the shot, so auto-exposure and focus adapt to the light. */
private const val TorchSettleMillis = 450L

// The journal list's polaroid (JournalScreen.JournalPolaroid); the viewfinder is that frame scaled up.
private val JournalPolaroidWidth = 170.dp
private val JournalPolaroidBorder = 10.dp
private val JournalCaptionMinHeight = 68.dp
private const val JournalPhotoAspect = 1.08f
private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

private val CameraPermissions = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private val CameraBackground = Color(0xFF070708)

/**
 * 拍照記錄. The viewfinder is the journal's polaroid frame, scaled up. A shot freezes in it for review:
 * retake (bottom left) or confirm (✓, where the shutter was). Confirming adds it to the journal and the
 * polaroid flies from the viewfinder into its spot in the list ([onPhotoTaken]).
 */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhotoTaken: (Photo) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = tripClipViewModel { app ->
        CameraViewModel(app.photoRepository, app.locationRepository, app.contentResolver)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reviewing = state.pending != null
    // Closing with an unconfirmed shot throws it away.
    val close = {
        viewModel.discardPending()
        onClose()
    }
    // Back while reviewing means "retake"; otherwise it closes the camera.
    BackHandler(onBack = { if (reviewing && state.stored == null) viewModel.retake() else close() })

    // Shutter feedback: a quick white flash over the viewfinder.
    val flash = remember { Animatable(0f) }
    var shots by remember { mutableIntStateOf(0) }
    LaunchedEffect(shots) {
        if (shots == 0) return@LaunchedEffect
        flash.snapTo(0.75f)
        flash.animateTo(0f, tween(260))
    }

    // Confirmed and stored: turn the frame to the tilt the photo has in the journal, then fly it there
    // (the frame now carries the photo's shared-element key).
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(state.stored) {
        val photo = state.stored ?: return@LaunchedEffect
        tilt.animateTo(polaroidTilt(photo.id), tween(220))
        onPhotoTaken(photo)
    }

    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(hasCamera()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = hasCamera()
        viewModel.startLocationUpdates()
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(CameraPermissions)
    }
    // Live location only while the camera is on screen.
    LifecycleStartEffect(Unit) {
        viewModel.startLocationUpdates()
        onStopOrDispose { viewModel.stopLocationUpdates() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CameraBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        CameraTopBar(flashOn = state.flashOn, onClose = close, onToggleFlash = viewModel::toggleFlash)

        if (!cameraGranted) {
            CameraPermissionPrompt(Modifier.weight(1f)) { permissionLauncher.launch(CameraPermissions) }
            return@Column
        }

        val camera = rememberCameraController(state.useFrontCamera, state.flashOn)
        val controller = camera.controller
        val failedText = stringResource(R.string.camera_failed)
        val scope = rememberCoroutineScope()

        PlaceLine(
            when {
                state.place != null -> state.place?.label()
                state.locating -> stringResource(R.string.camera_locating)
                else -> stringResource(R.string.camera_no_location)
            }
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ViewfinderPolaroid(
                controller = controller,
                pending = state.pending,
                stored = state.stored,
                tilt = tilt.value,
                flashAlpha = { flash.value },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Bottom left: retake, while a shot is being reviewed.
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = reviewing && state.stored == null,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f),
                ) {
                    RoundIconButton(
                        painter = painterResource(R.drawable.ic_retake),
                        contentDescription = stringResource(R.string.note_retake),
                        onClick = viewModel::retake,
                        size = 56.dp,
                    )
                }
            }
            // Centre: the shutter, which becomes ✓ (add to the journal) while reviewing.
            AnimatedContent(
                targetState = reviewing,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f))
                },
                label = "shutter",
            ) { review ->
                if (review) {
                    ConfirmButton(enabled = state.stored == null, onClick = viewModel::confirm)
                } else ShutterButton(
                enabled = camera.ready && !state.capturing,
                onClick = {
                    shots++
                    viewModel.onCaptureStarted()
                    scope.launch {
                        val useTorch = state.flashOn && !state.useFrontCamera &&
                            controller.cameraInfo?.hasFlashUnit() == true
                        if (useTorch) {
                            runCatching { controller.enableTorch(true).awaitResult() }
                            delay(TorchSettleMillis) // let auto-exposure adapt to the light
                        }
                        val torchOff = { if (useTorch) controller.enableTorch(false) }
                        capture(context, controller, state.location,
                            onSaved = { uri, takenAt ->
                                torchOff()
                                viewModel.onCaptured(uri, takenAt)
                            },
                            onError = {
                                torchOff()
                                viewModel.onCaptureFailed()
                                Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                },
            )
            }
            // Right: switch camera (hidden while reviewing).
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !reviewing,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f),
                ) {
                    RoundIconButton(
                        painter = painterResource(R.drawable.ic_flip_camera),
                        contentDescription = stringResource(R.string.camera_switch),
                        onClick = viewModel::switchCamera,
                        size = 56.dp,
                    )
                }
            }
        }
    }
}

/** Where the camera thinks you are, above the viewfinder. */
@Composable
private fun PlaceLine(label: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_location), null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.width(5.dp))
        Text(
            label.orEmpty(),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** ✓: adds the reviewed shot to the journal. Same size and colours as the shutter. */
@Composable
private fun ConfirmButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = PolaroidFrame,
        contentColor = PolaroidInk,
        modifier = Modifier.size(84.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.note_save), Modifier.size(36.dp))
        }
    }
}

/** A bound camera controller; [ready] turns true once CameraX has finished initializing. */
private class CameraHandle(val controller: LifecycleCameraController, val ready: Boolean)

@Composable
private fun rememberCameraController(useFront: Boolean, flashOn: Boolean): CameraHandle {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    // hasCamera(), takePicture() etc. throw until the initialization future completes.
    LaunchedEffect(controller) {
        ready = runCatching { controller.initializationFuture.awaitResult() }.isSuccess
    }
    LaunchedEffect(ready, useFront) {
        if (!ready) return@LaunchedEffect
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (controller.hasCamera(selector)) controller.cameraSelector = selector
    }
    // The back camera's flash is fired as a torch around the capture (see takePhoto), which is reliable
    // across devices; FLASH_MODE_ON alone often doesn't fire on some pipelines. The front camera has no
    // flash LED, so it uses CameraX's screen flash (lights the PreviewView white during the shot).
    LaunchedEffect(ready, flashOn, useFront) {
        if (!ready) return@LaunchedEffect
        controller.imageCaptureFlashMode =
            if (flashOn && useFront) ImageCapture.FLASH_MODE_SCREEN else ImageCapture.FLASH_MODE_OFF
    }
    return CameraHandle(controller, ready)
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addListener(
        { runCatching { get() }.fold(onSuccess = { cont.resume(it) }, onFailure = { cont.resumeWithException(it) }) },
        Runnable::run,
    )
    cont.invokeOnCancellation { cancel(false) }
}

private fun capture(
    context: Context,
    controller: LifecycleCameraController,
    location: android.location.Location?,
    onSaved: (android.net.Uri, Long) -> Unit,
    onError: () -> Unit,
) {
    val takenAt = System.currentTimeMillis()
    val name = "TripClip_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TripClip")
    }
    val options = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values,
    ).setMetadata(ImageCapture.Metadata().apply {
        this.location = location
        isReversedHorizontal = controller.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
    }).build()

    controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            output.savedUri?.let { onSaved(it, takenAt) } ?: onError()
        }

        override fun onError(exception: ImageCaptureException) = onError()
    })
}

@Composable
private fun CameraTopBar(flashOn: Boolean, onClose: () -> Unit, onToggleFlash: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        RoundIconButton(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.action_close),
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            stringResource(R.string.app_name),
            style = TextStyle(fontFamily = handwritingFontFamily(FontWeight.Bold), fontSize = 30.sp, color = Color.White),
        )
        Surface(
            onClick = onToggleFlash,
            modifier = Modifier.align(Alignment.CenterEnd).size(52.dp),
            shape = CircleShape,
            color = if (flashOn) PolaroidFrame else Color.White.copy(alpha = 0.08f),
            contentColor = if (flashOn) PolaroidInk else Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_flash), stringResource(R.string.camera_flash), Modifier.size(24.dp))
            }
        }
    }
}

/**
 * The viewfinder: exactly the journal list's polaroid (JournalScreen.JournalPolaroid), scaled to fit —
 * same border, 1.08 photo, caption area and handwritten time — with the live preview as its photo.
 * A [pending] shot freezes in it; once [stored] it carries the photo's shared-element key so it can
 * fly into the list.
 */
@Composable
private fun ViewfinderPolaroid(
    controller: LifecycleCameraController,
    pending: PendingShot?,
    stored: Photo?,
    tilt: Float,
    flashAlpha: () -> Float,
) {
    val hand = handwritingFontFamily()
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        // The journal polaroid's height / width (borders, 1.08 photo, minimum caption), so it fits either way.
        val b = JournalPolaroidBorder / JournalPolaroidWidth
        val heightRatio = 3 * b + (1 - 2 * b) / JournalPhotoAspect + JournalCaptionMinHeight / JournalPolaroidWidth
        val width = minOf(maxWidth, maxHeight / heightRatio)
        val k = width / JournalPolaroidWidth
        // Keep the live preview under the frozen shot until it has loaded, so there's no flash of grey.
        var shotLoaded by remember(pending?.uri) { mutableStateOf(false) }
        Polaroid(
            model = null,
            tiltDegrees = tilt,
            photoAspectRatio = JournalPhotoAspect,
            border = JournalPolaroidBorder * k,
            sharedPhotoId = stored?.id,
            modifier = Modifier.width(width),
            photo = {
                if (pending == null || !shotLoaded) PreviewArea(controller, flashAlpha, Modifier.fillMaxSize())
                if (pending != null) {
                    AsyncImage(
                        model = pending.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { shotLoaded = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        ) {
            val time = pending?.let { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalTime() } ?: now
            // Same caption as the list: the time in handwriting, scaled with the frame.
            Column(Modifier.heightIn(min = JournalCaptionMinHeight * k)) {
                Text(
                    time.format(ClockFormat),
                    style = TextStyle(fontFamily = hand, fontSize = 16.sp * k, color = PolaroidInk.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun PreviewArea(controller: LifecycleCameraController, flashAlpha: () -> Float, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(1.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // TextureView-based, so it draws correctly inside Compose layers (frame, clip, tilt).
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Rule-of-thirds grid.
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = 0.35f)
            val stroke = 1.dp.toPx()
            for (i in 1..2) {
                val x = size.width * i / 3
                val y = size.height * i / 3
                drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
            }
        }
        // Shutter flash.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flashAlpha() }
                .background(Color.White)
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(84.dp)
            .border(BorderStroke(4.dp, PolaroidFrame), CircleShape),
    ) {
        Box(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (enabled) PolaroidFrame else PolaroidFrame.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(modifier: Modifier, onRequest: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.camera_permission_body),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(stringResource(R.string.camera_permission_grant)) }
        }
    }
}
