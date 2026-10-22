package com.companionstudio.nyx.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.ui.theme.CategoryColorsDark
import com.companionstudio.nyx.ui.theme.CategoryColorsLight
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.ceil

/** The colour [category] has in the chart and its legend: fixed, so it means the same in both ranges. */
@Composable
fun categoryColor(category: Category): Color =
    (if (isSystemInDarkTheme()) CategoryColorsDark else CategoryColorsLight)[category.ordinal]

/**
 * S-10's chart (D-152): one bar per day, categories stacked bottom up in [Category] order. A day before tracking is a
 * dashed outline, not a bar of 0. TalkBack reads one summary: every number is in the day list below.
 * [onDayTap] gets the tapped day's index in [days].
 */
@Composable
fun TrendsChart(days: List<TrendDay>, range: TrendRange, onDayTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Category.entries.map { categoryColor(it) }
    val scheme = MaterialTheme.colorScheme
    val chrome = ChartChrome(grid = scheme.outlineVariant, empty = scheme.outline, label = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant))
    val measurer = rememberTextMeasurer()
    val description = stringResource(if (range == TrendRange.WEEK) R.string.trends_chart_week else R.string.trends_chart_month)
    val topSeconds = (ceil(days.maxOf { it.totalSeconds } / 3600.0).toLong().coerceAtLeast(1)) * 3600
    val axisLabels = listOf(topSeconds, topSeconds / 2).map(::formatDuration)
    val locale = LocalConfiguration.current.locales[0]
    val dayLabels = days.mapIndexed { i, day ->
        val back = days.size - 1 - i
        when {
            range == TrendRange.WEEK -> day.day.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale)
            back % 7 == 0 -> day.day.dayOfMonth.toString()
            else -> null
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
            .pointerInput(days) {
                detectTapGestures { offset ->
                    val layout = ChartLayout(size.width.toFloat(), size.height.toFloat(), days.size, axisWidth(measurer, axisLabels, chrome.label), labelHeight(measurer, chrome.label), gapPx(range))
                    layout.dayAt(offset.x)?.let(onDayTap)
                }
            },
    ) {
        val layout = ChartLayout(size.width, size.height, days.size, axisWidth(measurer, axisLabels, chrome.label), labelHeight(measurer, chrome.label), gapPx(range))
        drawGrid(layout, measurer, axisLabels, chrome)
        days.forEachIndexed { i, day ->
            val left = layout.barLeft(i)
            if (!day.tracked) {
                drawRect(
                    chrome.empty,
                    topLeft = Offset(left, layout.plotTop),
                    size = Size(layout.barWidth, layout.plotBottom - layout.plotTop),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            } else {
                drawStack(day, left, layout, topSeconds, colors)
            }
            dayLabels[i]?.let { label ->
                val text = measurer.measure(label, chrome.label)
                // Centred under its bar, but never past the canvas edge (today's label on 30 days)
                val x = (left + (layout.barWidth - text.size.width) / 2).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(x, layout.plotBottom + 4.dp.toPx()))
            }
        }
    }
}

private class ChartChrome(val grid: Color, val empty: Color, val label: TextStyle)

/** Where things go on the canvas, in pixels. The same numbers for drawing and for finding the tapped bar. */
private class ChartLayout(width: Float, height: Float, private val count: Int, axisWidth: Float, labelHeight: Float, private val gap: Float) {
    val plotLeft = axisWidth
    val plotTop = labelHeight / 2
    val plotBottom = height - labelHeight - gap
    val barWidth = ((width - plotLeft - gap * (count - 1)) / count).coerceAtLeast(1f)

    fun barLeft(index: Int) = plotLeft + index * (barWidth + gap)

    fun dayAt(x: Float): Int? = ((x - plotLeft) / (barWidth + gap)).toInt().takeIf { x >= plotLeft && it in 0 until count }
}

// 8 dp between 7 bars, 2 dp between 30 (§14.13)
private fun Density.gapPx(range: TrendRange) = (if (range == TrendRange.WEEK) 8.dp else 2.dp).toPx()

private fun Density.axisWidth(measurer: TextMeasurer, labels: List<String>, style: TextStyle) =
    labels.maxOf { measurer.measure(it, style).size.width } + 8.dp.toPx()

private fun labelHeight(measurer: TextMeasurer, style: TextStyle) = measurer.measure("0", style).size.height.toFloat()

/** Recessive grid lines at 0, half and the top, labelled on the left. */
private fun DrawScope.drawGrid(layout: ChartLayout, measurer: TextMeasurer, labels: List<String>, chrome: ChartChrome) {
    val half = (layout.plotTop + layout.plotBottom) / 2
    listOf(layout.plotTop to labels[0], half to labels[1], layout.plotBottom to null).forEach { (y, label) ->
        drawLine(chrome.grid, Offset(layout.plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        label?.let {
            val text = measurer.measure(it, chrome.label)
            drawText(text, topLeft = Offset(0f, y - text.size.height / 2))
        }
    }
}

/** One day's categories, bottom up, with a 2 dp gap between segments and a rounded top end. */
private fun DrawScope.drawStack(day: TrendDay, left: Float, layout: ChartLayout, topSeconds: Long, colors: List<Color>) {
    val plotHeight = layout.plotBottom - layout.plotTop
    val gap = 2.dp.toPx()
    val segments = Category.entries.mapNotNull { category -> day.seconds[category]?.let { category to it } }
    var bottom = layout.plotBottom
    segments.forEachIndexed { i, (category, seconds) ->
        val height = seconds.toFloat() / topSeconds * plotHeight
        val top = bottom - height
        // A sliver thinner than the gap would vanish in it; it still counts in the list
        val visibleTop = if (i == segments.lastIndex) top else top + gap
        if (bottom - visibleTop >= 1f) {
            val radius = if (i == segments.lastIndex) CornerRadius(minOf(4.dp.toPx(), layout.barWidth / 2, bottom - visibleTop)) else CornerRadius.Zero
            val path = Path().apply {
                addRoundRect(RoundRect(left, visibleTop, left + layout.barWidth, bottom, topLeftCornerRadius = radius, topRightCornerRadius = radius))
            }
            drawPath(path, colors[category.ordinal])
        }
        bottom = top
    }
}
