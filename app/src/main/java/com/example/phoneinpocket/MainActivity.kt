package com.example.phoneinpocket

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.phoneinpocket.ui.theme.AlertCoral
import com.example.phoneinpocket.ui.theme.AmberGlow
import com.example.phoneinpocket.ui.theme.AquaMist
import com.example.phoneinpocket.ui.theme.Ink700
import com.example.phoneinpocket.ui.theme.Ink800
import com.example.phoneinpocket.ui.theme.Ink900
import com.example.phoneinpocket.ui.theme.PaperWhite
import com.example.phoneinpocket.ui.theme.PhoneInPocketTheme
import com.example.phoneinpocket.ui.theme.SlateMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private var service: VibrationService? by mutableStateOf(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? VibrationService.LocalBinder)?.service()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneInPocketTheme {
                AppScreen(service = service)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, VibrationService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(connection)
        super.onStop()
    }
}

@Composable
private fun AppScreen(service: VibrationService?) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val vibrator = remember { VibrationPattern.defaultVibrator(context) }
    val hasVibrator = remember { vibrator.hasVibrator() }

    val state by remember(service) { service?.state ?: MutableStateFlow(VibrationState()) }
        .collectAsState()
    val running = state.isRunning

    var interval by remember { mutableIntStateOf(VibrationService.DEFAULT_INTERVAL) }
    var pattern by remember { mutableStateOf(VibrationPattern.CLICK) }

    LaunchedEffect(running) {
        if (running) {
            interval = state.intervalSeconds
            pattern = VibrationPattern.fromId(state.patternId)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            ContextCompat.startForegroundService(
                context, VibrationService.startIntent(context, interval, pattern.id)
            )
        }
    }

    fun requestStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ContextCompat.startForegroundService(
                context, VibrationService.startIntent(context, interval, pattern.id)
            )
        }
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            nowMs = System.currentTimeMillis()
            delay(60)
        }
    }
    val intervalMs = state.intervalSeconds * 1000L
    val remainingMs = if (running) (state.nextVibrationAt - nowMs).coerceIn(0, intervalMs) else 0L
    val progress = if (running && intervalMs > 0) remainingMs.toFloat() / intervalMs else 0f
    val secondsLeft = ceil(remainingMs / 1000.0).toInt()
    val pulse = if (running) 1f - progress else 0f

    val flash = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state.lastVibrationAt) {
        if (state.lastVibrationAt != 0L) {
            flash.snapTo(1f)
            pop.snapTo(1.16f)
            launch { flash.animateTo(0f, tween(600)) }
            pop.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientBackground(pulse = pulse)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 36.dp)
        ) {
            Header(running = running)

            Spacer(Modifier.height(34.dp))

            CountdownDial(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(272.dp),
                running = running,
                progress = progress,
                valueText = if (running) secondsLeft.toString() else interval.toString(),
                caption = if (running) stringResource(R.string.dial_caption_running) else stringResource(R.string.dial_caption_idle),
                flash = flash.value,
                popScale = pop.value,
            )

            if (!hasVibrator) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.no_vibrator_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertCoral,
                )
            }

            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.section_interval))
            Spacer(Modifier.height(12.dp))
            IntervalControls(interval = interval, enabled = !running) { interval = it }

            Spacer(Modifier.height(30.dp))
            SectionLabel(stringResource(R.string.section_feeling))
            Spacer(Modifier.height(10.dp))
            VibrationPattern.entries.forEach { p ->
                PatternRow(
                    pattern = p,
                    selected = pattern == p,
                    enabled = !running,
                    onSelect = {
                        pattern = p
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onPreview = {
                        p.execute(vibrator)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(26.dp))
            StartStopButton(
                running = running,
                onStart = { requestStart() },
                onStop = { service?.stop() },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.background_service_note),
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
            )
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.section_about).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = AquaMist,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.about_author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                val context = LocalContext.current
                Text(
                    text = "Alexander Harebava",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AmberGlow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AlexanderHarebava"))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {

                            }
                        }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "github.com/AlexanderHarebava",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.header_tag),
                style = MaterialTheme.typography.labelLarge,
                color = AquaMist,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(10.dp))

        }
        StatusPill(running = running)
    }
}

@Composable
private fun StatusPill(running: Boolean) {
    val transition = rememberInfiniteTransition(label = "status")
    val dotAlpha by transition.animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot"
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = Ink800.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Ink700),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (running) AmberGlow else SlateMuted.copy(alpha = 0.6f), CircleShape)
                    .alpha(if (running) dotAlpha else 1f)
            )
            Text(
                if (running) stringResource(R.string.status_active) else stringResource(R.string.status_off),
                style = MaterialTheme.typography.labelMedium,
                color = if (running) AmberGlow else SlateMuted,
            )
        }
    }
}

@Composable
private fun CountdownDial(
    modifier: Modifier = Modifier,
    running: Boolean,
    progress: Float,
    valueText: String,
    caption: String,
    flash: Float,
    popScale: Float,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeW = 9.dp.toPx()
            val inset = strokeW / 2 + 16.dp.toPx()
            val arcDiameter = size.minDimension - inset * 2
            val center = Offset(size.width / 2, size.height / 2)
            val arcTopLeft = Offset(center.x - arcDiameter / 2, center.y - arcDiameter / 2)
            val arcSize = Size(arcDiameter, arcDiameter)

            val outerR = size.minDimension / 2 - 2.dp.toPx()
            val innerR = outerR - 6.dp.toPx()
            val tickThick = 2.4.dp.toPx()
            val tickThin = 1.dp.toPx()
            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                drawLine(
                    color = Ink700,
                    start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                    end = Offset(center.x + outerR * cosA, center.y + outerR * sinA),
                    strokeWidth = if (i % 5 == 0) tickThick else tickThin,
                )
            }

            if (running) {
                drawCircle(
                    color = Ink700, radius = arcDiameter / 2, center = center, style = Stroke(strokeW)
                )
                if (flash > 0.01f) {
                    drawCircle(
                        color = AmberGlow.copy(alpha = 0.5f * flash),
                        radius = arcDiameter / 2 + (1f - flash) * 22.dp.toPx(),
                        center = center,
                        style = Stroke(1f + strokeW * flash),
                    )
                }
                drawArc(
                    color = AmberGlow,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round),
                )
            } else {
                drawCircle(
                    color = SlateMuted.copy(alpha = 0.4f),
                    radius = arcDiameter / 2,
                    center = center,
                    style = Stroke(
                        2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())),
                    ),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.displayLarge,
                color = if (running) AmberGlow else PaperWhite,
                modifier = Modifier.graphicsLayer { scaleX = popScale; scaleY = popScale },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = SlateMuted,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun IntervalControls(interval: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val presets = listOf(3, 5, 10, 15, 30, 60)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { value ->
            val selected = interval == value
            Surface(
                onClick = { onPick(value) },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                color = if (selected) AmberGlow else Ink800,
                contentColor = if (selected) Ink900 else SlateMuted,
                border = if (selected) null else BorderStroke(1.dp, Ink700),
            ) {
                Text(
                    stringResource(R.string.seconds_short, value),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton("−", enabled && interval > VibrationService.MIN_INTERVAL) { onPick(interval - 1) }
        Text(
            stringResource(R.string.seconds_full, interval),
            style = MaterialTheme.typography.headlineMedium,
            color = PaperWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("+", enabled && interval < VibrationService.MAX_INTERVAL) { onPick(interval + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Ink800,
        contentColor = if (enabled) AmberGlow else SlateMuted.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Ink700),
        modifier = Modifier.size(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PatternRow(
    pattern: VibrationPattern,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Ink700.copy(alpha = 0.55f) else Ink800.copy(alpha = 0.6f),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AmberGlow else Ink700),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaveformIcon(pattern.visual, color = if (selected) AmberGlow else AquaMist)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pattern.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = PaperWhite,
                )
                Text(
                    pattern.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted,
                )
            }
            Surface(
                onClick = onPreview,
                shape = CircleShape,
                color = Ink700,
                contentColor = if (selected) AmberGlow else PaperWhite,
                modifier = Modifier.size(40.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.preview_vibration_desc),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformIcon(bars: List<Float>, color: Color) {
    Canvas(Modifier.size(width = 44.dp, height = 26.dp)) {
        val barWidth = 4.dp.toPx()
        val gap = (size.width - bars.size * barWidth) / (bars.size - 1).coerceAtLeast(1)
        bars.forEachIndexed { index, height ->
            val barHeight = height.coerceIn(0.08f, 1f) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun StartStopButton(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, animationSpec = tween(120), label = "press"
    )
    val backgroundColor by animateColorAsState(
        if (running) AlertCoral else AmberGlow, animationSpec = tween(350), label = "bg"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(backgroundColor, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                if (running) onStop() else onStart()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (running) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start),
            color = Ink900,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = AquaMist,
        letterSpacing = 3.sp,
    )
}

@Composable
private fun AmbientBackground(pulse: Float) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val breathe by transition.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Canvas(Modifier.fillMaxSize()) {
        val amberCenter = Offset(size.width * 0.86f, size.height * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(AmberGlow.copy(alpha = 0.07f * breathe + 0.16f * pulse), Color.Transparent),
                center = amberCenter,
                radius = size.width * 1.05f,
            ),
            radius = size.width * 1.05f,
            center = amberCenter,
        )
        val aquaCenter = Offset(size.width * 0.06f, size.height * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(AquaMist.copy(alpha = 0.05f * breathe + 0.07f * pulse), Color.Transparent),
                center = aquaCenter,
                radius = size.width * 0.95f,
            ),
            radius = size.width * 0.95f,
            center = aquaCenter,
        )
    }
}
