package com.demomart.client.anim

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demomart.client.account.BalanceEffect
import com.demomart.client.ui.formatEur
import com.demomart.client.ui.formatPoints
import com.demomart.client.ui.formatPointsDelta
import com.demomart.client.ui.theme.Cream
import com.demomart.client.ui.theme.EarnGreen
import com.demomart.client.ui.theme.RedeemOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

fun effectLabel(effect: BalanceEffect): List<String> = when (effect) {
    is BalanceEffect.Redeem -> buildList {
        add("${formatPoints(-effect.redeemed)} · ${formatEur(effect.discountCents)} off")
        if (effect.earned > 0) add("${formatPointsDelta(effect.earned)} earned")
    }
    is BalanceEffect.Earn -> listOf(formatPointsDelta(effect.earned))
    is BalanceEffect.Refund -> listOf("Refund · ${formatPointsDelta(effect.delta)}")
}

fun countDurationMs(effect: BalanceEffect): Int = if (effect is BalanceEffect.Redeem) 1200 else 800

private val BalanceEffect.delta: Long
    get() = when (this) {
        is BalanceEffect.Redeem -> earned - redeemed
        is BalanceEffect.Earn -> earned
        is BalanceEffect.Refund -> delta
    }

@Composable
fun AnimatedBalance(balance: Long, effect: BalanceEffect?, onEffectShown: (BalanceEffect) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val onShown by rememberUpdatedState(onEffectShown)
    val progress = remember(effect?.key) { Animatable(if (effect == null) 1f else 0f) }
    val labels = remember(effect) { effect?.let(::effectLabel).orEmpty() }
    var labelIndex by remember(effect?.key) { mutableIntStateOf(-1) }
    LaunchedEffect(effect?.key) {
        if (effect == null) return@LaunchedEffect
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) progress.snapTo(1f)
        else launch { progress.animateTo(1f, tween(countDurationMs(effect))) }
        labelIndex = 0
        delay(3000)
        if (labels.size > 1) {
            labelIndex = 1
            delay(2000)
        }
        labelIndex = -1
        onShown(effect)
    }

    val elapsed = progress.value
    val shownBalance = balance - ((effect?.delta ?: 0L).toDouble() * (1 - elapsed)).roundToLong()
    val tint = when (effect) {
        is BalanceEffect.Redeem -> RedeemOrange
        is BalanceEffect.Earn -> EarnGreen
        else -> Color.White
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (effect != null && elapsed in 0f..<1f) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        tint.copy(alpha = (1 - elapsed) * 0.50f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                    if (effect is BalanceEffect.Redeem) {
                        for (i in 0 until 12) {
                            val origin = size.width * (i + 1) / 13
                            val drift = (i - 5.5f) * 5.dp.toPx() * elapsed
                            val rise = (26 + 5 * (i % 4)).dp.toPx() * elapsed
                            drawCircle(
                                Color(0xFFFFD186).copy(alpha = 1 - elapsed),
                                radius = (2 + i % 3).dp.toPx(),
                                center = Offset(origin + drift, size.height * 0.78f - rise),
                            )
                        }
                    }
                }
            }
            Text(formatPoints(shownBalance), color = Cream, fontWeight = FontWeight.SemiBold)
        }
        val label = labels.getOrNull(labelIndex)
        AnimatedVisibility(label != null, enter = fadeIn() + slideInVertically { it / 2 }) {
            if (label != null) {
                val redeem = effect is BalanceEffect.Redeem && labelIndex == 0
                val container = when {
                    redeem -> Color(0xFFFFD9AE)
                    effect is BalanceEffect.Refund -> Cream
                    else -> Color(0xFFD5ECCC)
                }
                Surface(color = container, contentColor = Color(0xFF18281D), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
