package com.alltestsareus.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object IsomColors {
    val Brown = Color(0xFF9A4D14)
    val Black = Color(0xFF111827)
    val Blue = Color(0xFF0284C7)
    val WaterFill = Color(0xFF38BDF8)
    val Green = Color(0xFF15803D)
    val DarkGreen = Color(0xFF166534)
    val Yellow = Color(0xFFFACC15)
    val LightForestBg = Color(0xFFDCFCE7)
    val CpMagenta = Color(0xFFC026D3)
}

/**
 * Отрисовка условных знаков спортивной карты (ISOM 2017-2) и пиктограмм легенд КП (IOF)
 * в стандартных картографических цветах через Jetpack Compose Canvas.
 */
@Composable
fun OrienteeringSignBadge(
    symbolCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp
) {
    val isMapSign = symbolCode.startsWith("isom_")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(Color(0xFFFAFAFA), RoundedCornerShape(10.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val sw = w * 0.085f
                val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

                when (symbolCode) {
                    // Коричневые знаки рельефа (ISOM)
                    "isom_knoll" -> {
                        drawOval(IsomColors.Brown, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.44f), style = stroke)
                        drawCircle(IsomColors.Brown, w * 0.06f, Offset(w * 0.50f, h * 0.50f))
                    }
                    "isom_pit" -> drawPitV(IsomColors.Brown, stroke)
                    "isom_depression" -> {
                        drawOval(IsomColors.Brown, Offset(w * 0.14f, h * 0.24f), Size(w * 0.72f, h * 0.52f), style = stroke)
                        drawLine(IsomColors.Brown, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.40f), sw * 0.9f)
                        drawLine(IsomColors.Brown, Offset(w * 0.50f, h * 0.76f), Offset(w * 0.50f, h * 0.60f), sw * 0.9f)
                    }
                    "isom_erosion_gully" -> {
                        val p = Path().apply {
                            moveTo(w * 0.16f, h * 0.76f)
                            lineTo(w * 0.82f, h * 0.22f)
                            lineTo(w * 0.34f, h * 0.84f)
                            close()
                        }
                        drawPath(p, color = IsomColors.Brown)
                    }
                    "isom_earth_bank" -> drawBankWithTicks(IsomColors.Brown, sw)
                    "isom_special_relief_x" -> drawCrossX(IsomColors.Brown, sw * 1.2f)

                    // Чёрные знаки камней, скал, троп и построек (ISOM)
                    "isom_boulder" -> drawCircle(IsomColors.Black, w * 0.20f, Offset(w * 0.50f, h * 0.50f))
                    "isom_boulder_cluster" -> drawFilledTriangle(IsomColors.Black)
                    "isom_cliff" -> drawBankWithTicks(IsomColors.Black, sw * 1.15f)
                    "isom_rocky_pit" -> drawPitV(IsomColors.Black, stroke)
                    "isom_stone_wall" -> {
                        drawLine(IsomColors.Black, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.90f, h * 0.50f), sw * 0.9f)
                        listOf(0.22f, 0.50f, 0.78f).forEach { x ->
                            drawCircle(IsomColors.Black, w * 0.08f, Offset(w * x, h * 0.50f))
                        }
                    }
                    "isom_ruin" -> {
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.48f, h * 0.22f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.20f, h * 0.78f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.78f), Offset(w * 0.80f, h * 0.78f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.80f, h * 0.78f), Offset(w * 0.80f, h * 0.45f), sw)
                    }
                    "isom_path_junction" -> {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.10f), 0f)
                        drawLine(IsomColors.Black, Offset(w * 0.12f, h * 0.16f), Offset(w * 0.88f, h * 0.84f), sw, pathEffect = dash)
                        drawLine(IsomColors.Black, Offset(w * 0.88f, h * 0.16f), Offset(w * 0.12f, h * 0.84f), sw, pathEffect = dash)
                    }
                    "isom_special_manmade_x" -> drawCrossX(IsomColors.Black, sw * 1.2f)
                    "isom_special_manmade_o" -> drawRingO(IsomColors.Black, stroke)

                    // Синие знаки гидрографии (ISOM)
                    "isom_marsh_passable" -> drawMarshBars(IsomColors.Blue, sw)
                    "isom_marsh_impassable" -> {
                        drawMarshBars(IsomColors.Blue, sw * 0.85f, insetX = 0.18f)
                        drawOval(IsomColors.Black, Offset(w * 0.12f, h * 0.20f), Size(w * 0.76f, h * 0.60f), style = Stroke(sw * 0.8f))
                    }
                    "isom_waterhole" -> {
                        drawOval(IsomColors.WaterFill, Offset(w * 0.18f, h * 0.26f), Size(w * 0.64f, h * 0.48f))
                        drawOval(IsomColors.Black, Offset(w * 0.18f, h * 0.26f), Size(w * 0.64f, h * 0.48f), style = Stroke(sw * 0.65f))
                    }
                    "isom_spring" -> {
                        drawArc(IsomColors.Blue, 120f, 220f, false, Offset(w * 0.18f, h * 0.28f), Size(w * 0.38f, h * 0.44f), style = stroke)
                        drawLine(IsomColors.Blue, Offset(w * 0.42f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), sw)
                    }

                    // Зелёные и жёлтые знаки растительности (ISOM)
                    "isom_root_stock_veg" -> drawCrossX(IsomColors.Green, sw * 1.2f)
                    "isom_distinct_tree" -> drawRingO(IsomColors.Green, stroke)
                    "isom_small_clearing" -> {
                        drawRect(IsomColors.LightForestBg, Offset.Zero, this.size)
                        drawOval(IsomColors.Yellow, Offset(w * 0.20f, h * 0.24f), Size(w * 0.60f, h * 0.52f))
                    }
                    "isom_thicket" -> drawOval(IsomColors.DarkGreen, Offset(w * 0.14f, h * 0.20f), Size(w * 0.72f, h * 0.60f))

                    // Пиктограммы легенд КП (IOF Control Descriptions) — Колонка D (Объект КП)
                    "iof_knoll" -> {
                        drawOval(IsomColors.Black, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.44f), style = stroke)
                        drawCircle(IsomColors.Black, w * 0.06f, Offset(w * 0.50f, h * 0.50f))
                    }
                    "iof_spur" -> {
                        drawArc(IsomColors.Black, 180f, 180f, false, Offset(w * 0.22f, h * 0.24f), Size(w * 0.56f, h * 0.52f), style = stroke)
                    }
                    "iof_reentrant" -> {
                        val p = Path().apply {
                            moveTo(w * 0.20f, h * 0.72f)
                            lineTo(w * 0.50f, h * 0.26f)
                            lineTo(w * 0.80f, h * 0.72f)
                        }
                        drawPath(p, color = IsomColors.Black, style = stroke)
                    }
                    "iof_pit" -> drawPitV(IsomColors.Black, stroke)
                    "iof_depression" -> {
                        drawOval(IsomColors.Black, Offset(w * 0.16f, h * 0.26f), Size(w * 0.68f, h * 0.48f), style = stroke)
                        drawLine(IsomColors.Black, Offset(w * 0.50f, h * 0.26f), Offset(w * 0.50f, h * 0.42f), sw * 0.9f)
                        drawLine(IsomColors.Black, Offset(w * 0.50f, h * 0.74f), Offset(w * 0.50f, h * 0.58f), sw * 0.9f)
                    }
                    "iof_cliff" -> drawBankWithTicks(IsomColors.Black, sw * 1.1f)
                    "iof_boulder" -> drawFilledTriangle(IsomColors.Black)
                    "iof_boulder_cluster" -> {
                        drawSmallTriangle(w * 0.50f, h * 0.28f, w * 0.16f, IsomColors.Black)
                        drawSmallTriangle(w * 0.30f, h * 0.68f, w * 0.16f, IsomColors.Black)
                        drawSmallTriangle(w * 0.70f, h * 0.68f, w * 0.16f, IsomColors.Black)
                    }
                    "iof_marsh" -> drawMarshBars(IsomColors.Black, sw)
                    "iof_spring" -> {
                        drawArc(IsomColors.Black, 120f, 220f, false, Offset(w * 0.18f, h * 0.28f), Size(w * 0.38f, h * 0.44f), style = stroke)
                        drawLine(IsomColors.Black, Offset(w * 0.42f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), sw)
                    }
                    "iof_clearing" -> {
                        drawRect(IsomColors.Black, Offset(w * 0.20f, h * 0.24f), Size(w * 0.60f, h * 0.52f), style = stroke)
                    }
                    "iof_distinct_tree" -> {
                        drawCircle(IsomColors.Black, w * 0.20f, Offset(w * 0.50f, h * 0.42f), style = stroke)
                        drawLine(IsomColors.Black, Offset(w * 0.50f, h * 0.62f), Offset(w * 0.50f, h * 0.84f), sw)
                    }
                    "iof_root_stock" -> drawCrossX(IsomColors.Black, sw * 1.15f)
                    "iof_ruin" -> {
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.48f, h * 0.22f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.20f, h * 0.78f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.20f, h * 0.78f), Offset(w * 0.80f, h * 0.78f), sw)
                        drawLine(IsomColors.Black, Offset(w * 0.80f, h * 0.78f), Offset(w * 0.80f, h * 0.45f), sw)
                    }
                    "iof_path_junction" -> {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.10f), 0f)
                        drawLine(IsomColors.Black, Offset(w * 0.14f, h * 0.16f), Offset(w * 0.86f, h * 0.84f), sw, pathEffect = dash)
                        drawLine(IsomColors.Black, Offset(w * 0.86f, h * 0.16f), Offset(w * 0.14f, h * 0.84f), sw, pathEffect = dash)
                    }

                    // Колонка G — Положение призмы КП относительно объекта
                    "iof_pos_n" -> drawSideDot(0.50f, 0.22f, sw)
                    "iof_pos_ne" -> drawSideDot(0.76f, 0.24f, sw)
                    "iof_pos_s" -> drawSideDot(0.50f, 0.78f, sw)
                    "iof_pos_w" -> drawSideDot(0.22f, 0.50f, sw)
                    "iof_pos_top" -> {
                        drawArc(IsomColors.Black, 180f, 180f, false, Offset(w * 0.20f, h * 0.42f), Size(w * 0.60f, h * 0.42f), style = stroke)
                        drawCircle(Color(0xFFDC2626), w * 0.10f, Offset(w * 0.50f, h * 0.25f))
                    }
                    "iof_pos_foot" -> {
                        drawLine(IsomColors.Black, Offset(w * 0.22f, h * 0.26f), Offset(w * 0.58f, h * 0.76f), sw, StrokeCap.Round)
                        drawLine(IsomColors.Black, Offset(w * 0.58f, h * 0.76f), Offset(w * 0.84f, h * 0.76f), sw, StrokeCap.Round)
                        drawCircle(Color(0xFFDC2626), w * 0.10f, Offset(w * 0.68f, h * 0.62f))
                    }
                    "iof_pos_inside" -> {
                        drawPitV(IsomColors.Black, stroke)
                        drawCircle(Color(0xFFDC2626), w * 0.10f, Offset(w * 0.50f, h * 0.46f))
                    }
                    "iof_pos_between" -> {
                        drawSmallTriangle(w * 0.25f, h * 0.52f, w * 0.16f, IsomColors.Black)
                        drawSmallTriangle(w * 0.75f, h * 0.52f, w * 0.16f, IsomColors.Black)
                        drawCircle(Color(0xFFDC2626), w * 0.10f, Offset(w * 0.50f, h * 0.52f))
                    }

                    // Колонки C и H
                    "iof_col_c_north" -> {
                        drawCircle(Color(0xFFDC2626), w * 0.11f, Offset(w * 0.50f, h * 0.28f))
                        drawCircle(IsomColors.Black, w * 0.10f, Offset(w * 0.50f, h * 0.68f), style = Stroke(sw * 0.85f))
                    }
                    "iof_col_h_first_aid" -> {
                        drawLine(Color(0xFFDC2626), Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), sw * 1.4f, StrokeCap.Round)
                        drawLine(Color(0xFFDC2626), Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), sw * 1.4f, StrokeCap.Round)
                    }
                    "iof_col_h_refreshment" -> {
                        val p = Path().apply {
                            moveTo(w * 0.26f, h * 0.24f)
                            lineTo(w * 0.74f, h * 0.24f)
                            lineTo(w * 0.64f, h * 0.78f)
                            lineTo(w * 0.36f, h * 0.78f)
                            close()
                        }
                        drawPath(p, color = IsomColors.Black, style = stroke)
                    }

                    else -> drawCircle(color = IsomColors.CpMagenta, radius = w * 0.34f, style = stroke)
                }

                if (isMapSign) {
                    drawCircle(
                        color = IsomColors.CpMagenta.copy(alpha = 0.45f),
                        radius = w * 0.46f,
                        center = Offset(w * 0.50f, h * 0.50f),
                        style = Stroke(width = sw * 0.45f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isMapSign) "Знак карты" else "Знак КП",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8)
        )
    }
}

private fun DrawScope.drawCrossX(color: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawPitV(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.22f, h * 0.28f)
        lineTo(w * 0.50f, h * 0.76f)
        lineTo(w * 0.78f, h * 0.28f)
    }
    drawPath(p, color = color, style = stroke)
}

private fun DrawScope.drawRingO(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(color = color, radius = w * 0.24f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
}

private fun DrawScope.drawBankWithTicks(color: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.10f, h * 0.36f), Offset(w * 0.90f, h * 0.36f), strokeWidth, StrokeCap.Round)
    listOf(0.24f, 0.50f, 0.76f).forEach { x ->
        drawLine(color, Offset(w * x, h * 0.36f), Offset(w * x, h * 0.66f), strokeWidth * 0.85f, StrokeCap.Round)
    }
}

private fun DrawScope.drawFilledTriangle(color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.50f, h * 0.18f)
        lineTo(w * 0.82f, h * 0.78f)
        lineTo(w * 0.18f, h * 0.78f)
        close()
    }
    drawPath(p, color = color)
}

private fun DrawScope.drawMarshBars(color: Color, strokeWidth: Float, insetX: Float = 0.14f) {
    val w = size.width
    val h = size.height
    listOf(0.30f, 0.50f, 0.70f).forEach { y ->
        drawLine(color, Offset(w * insetX, h * y), Offset(w * (1f - insetX), h * y), strokeWidth, StrokeCap.Round)
    }
}

private fun DrawScope.drawSmallTriangle(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy + r * 0.85f)
        lineTo(cx - r, cy + r * 0.85f)
        close()
    }
    drawPath(p, color = color)
}

private fun DrawScope.drawSideDot(dotX: Float, dotY: Float, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    drawCircle(IsomColors.Black, w * 0.20f, Offset(w * 0.50f, h * 0.50f), style = Stroke(strokeWidth * 0.85f))
    drawCircle(Color(0xFFDC2626), w * 0.11f, Offset(w * dotX, h * dotY))
}
