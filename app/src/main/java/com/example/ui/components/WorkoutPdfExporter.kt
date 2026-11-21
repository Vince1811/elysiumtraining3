package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.CompletedSetInfo
import com.example.data.WorkoutCategory
import com.example.data.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfThemeColors(
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outlineVariant: Int
)

suspend fun exportCurrentSessionPdf(
    sets: List<WorkoutSet>,
    categories: List<WorkoutCategory>,
    username: String,
    colors: PdfThemeColors,
    context: Context
) {
    if (sets.isEmpty()) {
        Toast.makeText(context, "No exercises to export", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val pdfFile = withContext(Dispatchers.IO) {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 26f
            val contentWidth = pageWidth - (margin * 2f)
            val footerLimit = pageHeight - 36f

            val doc = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.background
                style = Paint.Style.FILL
            }
            val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.surface
                style = Paint.Style.FILL
            }
            val surfaceVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.surfaceVariant
                style = Paint.Style.FILL
            }
            val primaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                style = Paint.Style.FILL
            }
            val primaryContainerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primaryContainer
                style = Paint.Style.FILL
            }
            val outlineStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.outlineVariant
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            val primaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                style = Paint.Style.STROKE
                strokeWidth = 2.0f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurface
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurfaceVariant
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurface
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.secondary
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val badgeOnPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onPrimary
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val badgeOnContainerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onPrimaryContainer
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val circleNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurface
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val circleWeightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            fun drawPageBackgroundAndFooter(c: Canvas, pNum: Int) {
                c.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                val footerText = "Elysium Training  •  Today's Program Export"
                c.drawText(footerText, margin, pageHeight - 16f, mutedPaint)
                val pageLabel = "Page $pNum"
                val w = mutedPaint.measureText(pageLabel)
                c.drawText(pageLabel, pageWidth - margin - w, pageHeight - 16f, mutedPaint)
            }

            fun decodeSetLogs(set: WorkoutSet): List<CompletedSetInfo> {
                return try {
                    if (set.completedSetsJson.isNotBlank()) {
                        Json.decodeFromString<List<CompletedSetInfo>>(set.completedSetsJson)
                    } else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val totalDoneExercises = sets.count { it.completedSets >= it.targetSets && it.targetSets > 0 }
            val totalLoggedSets = sets.sumOf { set ->
                val list = decodeSetLogs(set)
                if (list.isNotEmpty()) list.size else set.completedSets
            }
            val totalTargetSetsAll = sets.sumOf { it.targetSets }
            val totalVolumeKg = sets.sumOf { set ->
                val list = decodeSetLogs(set)
                if (list.isNotEmpty()) {
                    list.sumOf { it.reps * it.weightKg }
                } else {
                    set.completedReps * set.completedWeightKg
                }
            }

            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy  HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            val displayUser = username.ifBlank { "Athlete" }

            fun drawHeader(c: Canvas): Float {
                val headerRect = RectF(margin, margin, pageWidth - margin, margin + 84f)
                c.drawRoundRect(headerRect, 14f, 14f, surfacePaint)
                c.drawRoundRect(headerRect, 14f, 14f, outlineStrokePaint)

                // User badge & title matching app top bar + Today's Program header
                c.drawText("Today's Program", margin + 14f, margin + 28f, titlePaint)
                c.drawText("$displayUser  •  $dateStr", margin + 14f, margin + 45f, subtitlePaint)

                val summaryLine = "$totalDoneExercises / ${sets.size} Exercises Done   •   $totalLoggedSets / $totalTargetSetsAll Sets   •   ${
                    if (totalVolumeKg % 1.0 == 0.0) "${totalVolumeKg.toInt()}" else String.format(Locale.US, "%.1f", totalVolumeKg)
                } kg Volume"
                c.drawText(summaryLine, margin + 14f, margin + 63f, mutedPaint)

                // Top-right status pill matching app pill aesthetic
                val pillText = if (totalDoneExercises == sets.size && sets.isNotEmpty()) "COMPLETED" else "ACTIVE TRACKER"
                val pillWidth = badgeOnPrimaryPaint.measureText(pillText) + 20f
                val pillRect = RectF(
                    headerRect.right - 14f - pillWidth,
                    headerRect.top + 12f,
                    headerRect.right - 14f,
                    headerRect.top + 32f
                )
                c.drawRoundRect(pillRect, 10f, 10f, primaryFillPaint)
                c.drawText(pillText, pillRect.left + 10f, pillRect.top + 13.5f, badgeOnPrimaryPaint)

                // Accent tab underline matching Active Tracker tab bar
                val tabBarY = headerRect.bottom + 14f
                c.drawText("Added & Completed Exercises", margin + 4f, tabBarY, subtitlePaint)
                val lineY = tabBarY + 6f
                c.drawLine(margin, lineY, margin + 170f, lineY, primaryStrokePaint)
                c.drawLine(margin + 170f, lineY, pageWidth - margin, lineY, outlineStrokePaint)

                return lineY + 12f
            }

            drawPageBackgroundAndFooter(canvas, pageNumber)
            var currentY = drawHeader(canvas)

            sets.forEach { set ->
                val indList = decodeSetLogs(set)
                val indMap = indList.associateBy { it.setNumber }
                val isCompleted = set.completedSets >= set.targetSets && set.targetSets > 0
                val categoryName = categories.find { it.id == set.categoryId }?.name ?: "Workout"

                val circlesPerRow = 10
                val circleRows = ((set.targetSets.coerceAtLeast(1) - 1) / circlesPerRow) + 1
                val superSetHeight = if (set.isSuperSet) 16f else 0f
                val circlesHeight = circleRows * 34f
                val logSummaryText = if (indList.isNotEmpty()) {
                    "Logged: " + indList.sortedBy { it.setNumber }.joinToString("  •  ") { info ->
                        val wStr = if (info.weightKg % 1.0 == 0.0) "${info.weightKg.toInt()}kg" else String.format(Locale.US, "%.1fkg", info.weightKg)
                        "S${info.setNumber}: ${info.reps}r @ $wStr"
                    }
                } else if (set.completedSets > 0) {
                    val wStr = if (set.completedWeightKg % 1.0 == 0.0) "${set.completedWeightKg.toInt()}kg" else String.format(Locale.US, "%.1fkg", set.completedWeightKg)
                    "Logged: ${set.completedSets} sets • ${set.completedReps} total reps @ $wStr"
                } else {
                    "Added to program — pending sets"
                }

                // Wrap log summary if long
                val maxSummaryWidth = contentWidth - 24f
                val summaryLines = mutableListOf<String>()
                var remainingText = logSummaryText
                while (remainingText.isNotEmpty()) {
                    val count = mutedPaint.breakText(remainingText, true, maxSummaryWidth, null)
                    if (count <= 0) break
                    if (count >= remainingText.length) {
                        summaryLines.add(remainingText)
                        break
                    }
                    val splitAt = remainingText.lastIndexOf(' ', count).let { if (it > 0) it else count }
                    summaryLines.add(remainingText.substring(0, splitAt).trim())
                    remainingText = remainingText.substring(splitAt).trim()
                }
                val summaryHeight = (summaryLines.size.coerceAtLeast(1)) * 13f

                val cardHeight = 46f + superSetHeight + circlesHeight + summaryHeight + 12f

                if (currentY + cardHeight > footerLimit) {
                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageBackgroundAndFooter(canvas, pageNumber)
                    currentY = drawHeader(canvas)
                }

                val cardRect = RectF(margin, currentY, pageWidth - margin, currentY + cardHeight)
                canvas.drawRoundRect(cardRect, 12f, 12f, surfacePaint)
                canvas.drawRoundRect(
                    cardRect,
                    12f,
                    12f,
                    if (set.isSuperSet) primaryStrokePaint else outlineStrokePaint
                )

                var innerY = cardRect.top + 14f

                if (set.isSuperSet) {
                    val ssRect = RectF(cardRect.left + 12f, innerY - 8f, cardRect.left + 68f, innerY + 6f)
                    canvas.drawRoundRect(ssRect, 4f, 4f, primaryFillPaint)
                    canvas.drawText("SUPERSET", ssRect.left + 6f, ssRect.top + 10.5f, badgeOnPrimaryPaint)
                    innerY += 16f
                }

                // Title + Status pill
                val statusLabel = when {
                    isCompleted -> "DONE ✓"
                    set.completedSets > 0 -> "${set.completedSets}/${set.targetSets} DONE"
                    else -> "ADDED"
                }
                val statusWidth = badgeOnContainerPaint.measureText(statusLabel) + 18f
                val statusRect = RectF(
                    cardRect.right - 12f - statusWidth,
                    innerY - 9f,
                    cardRect.right - 12f,
                    innerY + 9f
                )
                canvas.drawRoundRect(
                    statusRect,
                    9f,
                    9f,
                    if (isCompleted) primaryFillPaint else if (set.completedSets > 0) primaryContainerPaint else surfaceVariantPaint
                )
                canvas.drawText(
                    statusLabel,
                    statusRect.left + 9f,
                    statusRect.top + 12.5f,
                    if (isCompleted) badgeOnPrimaryPaint else badgeOnContainerPaint
                )

                canvas.drawText(set.exerciseName, cardRect.left + 12f, innerY + 4f, cardTitlePaint)
                innerY += 17f
                canvas.drawText(
                    "$categoryName  •  ${set.targetSets} sets × ${set.targetReps} reps",
                    cardRect.left + 12f,
                    innerY,
                    secondaryPaint
                )
                innerY += 14f

                // Numbered Set Circles Row (exact visual match of ActiveWorkoutItem circles)
                val circleDiameter = 28f
                val circleGap = 8f
                for (i in 1..set.targetSets) {
                    val colIdx = (i - 1) % circlesPerRow
                    val rowIdx = (i - 1) / circlesPerRow
                    val cx = cardRect.left + 12f + (colIdx * (circleDiameter + circleGap)) + (circleDiameter / 2f)
                    val cy = innerY + (rowIdx * 34f) + (circleDiameter / 2f)
                    val radius = circleDiameter / 2f

                    val isSetDone = i <= set.completedSets
                    val loggedInfo = indMap[i]
                    val weightForCircle = loggedInfo?.weightKg ?: if (isSetDone) set.completedWeightKg else 0.0

                    canvas.drawCircle(
                        cx,
                        cy,
                        radius,
                        if (isSetDone) primaryContainerPaint else surfaceVariantPaint
                    )
                    canvas.drawCircle(
                        cx,
                        cy,
                        radius,
                        if (isSetDone || weightForCircle > 0.0) primaryStrokePaint else outlineStrokePaint
                    )

                    if (weightForCircle > 0.0) {
                        val wNum = if (weightForCircle % 1.0 == 0.0) {
                            "${weightForCircle.toInt()}kg"
                        } else {
                            String.format(Locale.US, "%.1f", weightForCircle)
                        }
                        canvas.drawText("$i", cx, cy - 1f, circleNumPaint)
                        canvas.drawText(wNum, cx, cy + 8.5f, circleWeightPaint)
                    } else {
                        canvas.drawText("$i", cx, cy + 3.5f, circleNumPaint)
                    }
                }

                innerY += circlesHeight + 6f
                summaryLines.forEach { line ->
                    canvas.drawText(line, cardRect.left + 12f, innerY, mutedPaint)
                    innerY += 13f
                }

                currentY = cardRect.bottom + 8f
            }

            doc.finishPage(page)

            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val outFile = File(context.cacheDir, "today_program_$dateStamp.pdf")
            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()
            outFile
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Today's Program - Workout PDF")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Workout PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

data class HistoryDayExport(
    val dateLabel: String,
    val sets: List<WorkoutSet>
)

suspend fun exportHistoryPdf(
    days: List<HistoryDayExport>,
    colors: PdfThemeColors,
    context: Context,
    fileName: String = "workout_history.pdf",
    title: String = "Workout History"
) {
    val nonEmptyDays = days.filter { it.sets.isNotEmpty() }
    if (nonEmptyDays.isEmpty()) {
        Toast.makeText(context, "No workout history to export", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val (pdfFile, savedToDownloads) = withContext(Dispatchers.IO) {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 26f
            val contentWidth = pageWidth - (margin * 2f)
            val footerLimit = pageHeight - 36f

            val doc = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.background
                style = Paint.Style.FILL
            }
            val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.surface
                style = Paint.Style.FILL
            }
            val surfaceVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.surfaceVariant
                style = Paint.Style.FILL
            }
            val primaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                style = Paint.Style.FILL
            }
            val primaryContainerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primaryContainer
                style = Paint.Style.FILL
            }
            val outlineStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.outlineVariant
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            val primaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurface
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.primary
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dayBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onPrimaryContainer
                textSize = 12.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurfaceVariant
                textSize = 9.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onSurface
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val badgeOnPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onPrimary
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val badgeOnContainerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.onPrimaryContainer
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            fun decodeSetLogs(set: WorkoutSet): List<CompletedSetInfo> {
                return try {
                    if (set.completedSetsJson.isNotBlank()) {
                        Json.decodeFromString<List<CompletedSetInfo>>(set.completedSetsJson)
                    } else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            fun drawPageBgAndFooter(c: Canvas, pNum: Int) {
                c.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                c.drawText("Elysium Training  •  Workout History PDF", margin, pageHeight - 16f, mutedPaint)
                val pStr = "Page $pNum"
                c.drawText(pStr, pageWidth - margin - mutedPaint.measureText(pStr), pageHeight - 16f, mutedPaint)
            }

            val totalExercises = nonEmptyDays.sumOf { it.sets.size }
            val totalVolumeKg = nonEmptyDays.sumOf { day ->
                day.sets.sumOf { set ->
                    val list = decodeSetLogs(set)
                    if (list.isNotEmpty()) list.sumOf { it.reps * it.weightKg }
                    else set.completedReps * set.completedWeightKg
                }
            }
            val exportedAt = SimpleDateFormat("EEE, dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date())

            fun startNewPage(): Float {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                drawPageBgAndFooter(canvas, pageNumber)
                return margin + 12f
            }

            drawPageBgAndFooter(canvas, pageNumber)

            // Top document header
            val headerRect = RectF(margin, margin, pageWidth - margin, margin + 74f)
            canvas.drawRoundRect(headerRect, 14f, 14f, surfacePaint)
            canvas.drawRoundRect(headerRect, 14f, 14f, outlineStrokePaint)
            canvas.drawText(title, margin + 14f, margin + 27f, titlePaint)
            canvas.drawText("Exported $exportedAt", margin + 14f, margin + 44f, subtitlePaint)
            val volStr = if (totalVolumeKg % 1.0 == 0.0) "${totalVolumeKg.toInt()}" else String.format(Locale.US, "%.1f", totalVolumeKg)
            canvas.drawText(
                "${nonEmptyDays.size} Session(s)  •  $totalExercises Exercise(s)  •  $volStr kg Total Volume",
                margin + 14f,
                margin + 61f,
                mutedPaint
            )

            var currentY = headerRect.bottom + 14f
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            nonEmptyDays.forEach { day ->
                val dayHeaderHeight = 30f
                if (currentY + dayHeaderHeight + 64f > footerLimit) {
                    currentY = startNewPage()
                }

                val dayRect = RectF(margin, currentY, pageWidth - margin, currentY + dayHeaderHeight)
                canvas.drawRoundRect(dayRect, 8f, 8f, primaryContainerPaint)
                canvas.drawText(day.dateLabel, dayRect.left + 12f, dayRect.top + 19.5f, dayBannerPaint)
                val dayCountText = "${day.sets.size} exercises"
                canvas.drawText(
                    dayCountText,
                    dayRect.right - 12f - badgeOnContainerPaint.measureText(dayCountText),
                    dayRect.top + 19.5f,
                    badgeOnContainerPaint
                )
                currentY = dayRect.bottom + 8f

                day.sets.forEach { set ->
                    val indList = decodeSetLogs(set)
                    val isCompleted = set.completedSets >= set.targetSets && set.targetSets > 0
                    val timeStr = if (set.timestamp != null) timeFormat.format(Date(set.timestamp)) else "N/A"
                    val summaryText = if (indList.isNotEmpty()) {
                        indList.sortedBy { it.setNumber }.joinToString("  •  ") { info ->
                            val wStr = if (info.weightKg % 1.0 == 0.0) "${info.weightKg.toInt()}kg" else String.format(Locale.US, "%.1fkg", info.weightKg)
                            "Set ${info.setNumber}: ${info.reps} reps @ $wStr"
                        }
                    } else {
                        val wStr = if (set.completedWeightKg % 1.0 == 0.0) "${set.completedWeightKg.toInt()}kg" else String.format(Locale.US, "%.1fkg", set.completedWeightKg)
                        "Completed: ${set.completedSets}/${set.targetSets} sets, ${set.completedReps} reps" +
                            if (set.completedWeightKg > 0.0) " @ $wStr" else ""
                    }

                    val maxTextWidth = contentWidth - 24f
                    val wrappedLines = mutableListOf<String>()
                    var rem = summaryText
                    while (rem.isNotEmpty()) {
                        val count = mutedPaint.breakText(rem, true, maxTextWidth, null)
                        if (count <= 0) break
                        if (count >= rem.length) {
                            wrappedLines.add(rem)
                            break
                        }
                        val splitAt = rem.lastIndexOf(' ', count).let { if (it > 0) it else count }
                        wrappedLines.add(rem.substring(0, splitAt).trim())
                        rem = rem.substring(splitAt).trim()
                    }

                    val superSetOffset = if (set.isSuperSet) 15f else 0f
                    val cardHeight = 46f + superSetOffset + (wrappedLines.size.coerceAtLeast(1) * 13f) + 10f

                    if (currentY + cardHeight > footerLimit) {
                        currentY = startNewPage()
                    }

                    val cardRect = RectF(margin, currentY, pageWidth - margin, currentY + cardHeight)
                    canvas.drawRoundRect(cardRect, 10f, 10f, surfacePaint)
                    canvas.drawRoundRect(
                        cardRect,
                        10f,
                        10f,
                        if (set.isSuperSet) primaryStrokePaint else outlineStrokePaint
                    )

                    var innerY = cardRect.top + 14f
                    if (set.isSuperSet) {
                        val ssRect = RectF(cardRect.left + 12f, innerY - 8f, cardRect.left + 68f, innerY + 5f)
                        canvas.drawRoundRect(ssRect, 4f, 4f, primaryFillPaint)
                        canvas.drawText("SUPERSET", ssRect.left + 6f, ssRect.top + 10f, badgeOnPrimaryPaint)
                        innerY += 15f
                    }

                    val statusText = if (isCompleted) "COMPLETED" else "${set.completedSets}/${set.targetSets} SETS"
                    val pillW = badgeOnPrimaryPaint.measureText(statusText) + 16f
                    val pillRect = RectF(cardRect.right - 12f - pillW, innerY - 9f, cardRect.right - 12f, innerY + 8f)
                    canvas.drawRoundRect(
                        pillRect,
                        8f,
                        8f,
                        if (isCompleted) primaryFillPaint else surfaceVariantPaint
                    )
                    canvas.drawText(
                        statusText,
                        pillRect.left + 8f,
                        pillRect.top + 12f,
                        if (isCompleted) badgeOnPrimaryPaint else badgeOnContainerPaint
                    )

                    canvas.drawText(set.exerciseName, cardRect.left + 12f, innerY + 4f, cardTitlePaint)
                    innerY += 17f
                    canvas.drawText(
                        "Target: ${set.targetSets} sets × ${set.targetReps} reps   •   Time: $timeStr",
                        cardRect.left + 12f,
                        innerY,
                        mutedPaint
                    )
                    innerY += 15f
                    wrappedLines.forEach { line ->
                        canvas.drawText(line, cardRect.left + 12f, innerY, mutedPaint)
                        innerY += 13f
                    }

                    currentY = cardRect.bottom + 7f
                }
                currentY += 6f
            }

            doc.finishPage(page)

            val outFile = File(context.cacheDir, fileName)
            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }

            var downloadedCopy = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            outFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        downloadedCopy = true
                    }
                } catch (_: Exception) {
                }
            }

            doc.close()
            outFile to downloadedCopy
        }

        if (savedToDownloads) {
            Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "$title (PDF)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $title PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
