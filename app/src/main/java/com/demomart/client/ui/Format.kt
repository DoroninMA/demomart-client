package com.demomart.client.ui

import kotlin.math.abs

fun formatEur(cents: Long): String {
    val sign = if (cents < 0) "−" else ""
    val euros = abs(cents / 100)
    val fraction = abs(cents % 100).toString().padStart(2, '0')
    return "${sign}€$euros.$fraction"
}

fun formatPoints(points: Long): String {
    val sign = if (points < 0) "−" else ""
    val digits = points.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString("\u2009").reversed()
    return "$sign$grouped pts"
}

fun formatPointsDelta(delta: Long): String =
    if (delta > 0) "+${formatPoints(delta)}" else formatPoints(delta)
