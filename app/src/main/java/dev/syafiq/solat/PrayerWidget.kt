package dev.syafiq.solat

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.widget.RemoteViews
import dev.syafiq.solat.data.COUNTDOWN
import dev.syafiq.solat.data.MYT
import dev.syafiq.solat.data.Prayer
import dev.syafiq.solat.data.Repo
import dev.syafiq.solat.data.countdown
import dev.syafiq.solat.data.dotOf
import dev.syafiq.solat.data.nextPrayer
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home-screen widget: the day as 144 dots (10 minutes each, 16 x 9), lit up to now.
 * Each prayer's dot is replaced by its letter (S, sun, Z, A, M, I); the next one is red, the rest white.
 * Drawn to a bitmap because RemoteViews can't lay out a dot grid or use the app's fonts.
 */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action in listOf(TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) refresh(ctx)
    }

    override fun onDisabled(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(tick(ctx))

    companion object {
        private const val TICK = "dev.syafiq.solat.WIDGET_TICK"

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (ids.isEmpty()) return
            val (bitmap, summary) = render(ctx, Instant.now())
            val views = RemoteViews(ctx.packageName, R.layout.widget_prayer).apply {
                setImageViewBitmap(R.id.widget_image, bitmap)
                setContentDescription(R.id.widget_image, summary)
                setOnClickPendingIntent(
                    R.id.widget_image,
                    PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            mgr.updateAppWidget(ids, views)
            // Non-wakeup alarm each minute for the countdown: it waits while the screen is off, costing no battery.
            val nextMinute = (System.currentTimeMillis() / 60_000 + 1) * 60_000
            ctx.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC, nextMinute, 5_000, tick(ctx))
        }

        private fun tick(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, PrayerWidget::class.java).setAction(TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private const val W = 600f
        private const val COLS = 16
        private const val ROWS = 9
        private const val BG = 0xFF1B1B1B.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DIM = 0xFF4A4A4A.toInt()
        private const val GREY = 0xFF8A8A8A.toInt()
        private const val RED = 0xFFD71921.toInt()
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
        private val LETTERS = mapOf(Prayer.FAJR to "S", Prayer.DHUHR to "Z", Prayer.ASR to "A", Prayer.MAGHRIB to "M", Prayer.ISHA to "I")

        private fun render(ctx: Context, now: Instant): Pair<Bitmap, String> {
            val repo = Repo(ctx)
            val zone = repo.zone
            val today = now.atZone(MYT).toLocalDate()
            val days = zone?.let { z ->
                listOf(YearMonth.from(today), YearMonth.from(today.plusDays(1))).distinct()
                    .flatMap { repo.cachedMonth(z.code, it).orEmpty() }.associateBy { it.date }
            }.orEmpty()
            val day = days[today]
            val next = nextPrayer(days, now)
            val nowDot = dotOf(now)
            val marks = day?.let { d -> COUNTDOWN.associateBy { dotOf(d.times.getValue(it)) } }.orEmpty()

            val bmp = Bitmap.createBitmap(W.toInt(), W.toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = BG
            c.drawRoundRect(0f, 0f, W, W, W * 0.14f, W * 0.14f, p)

            val left = W * 0.11f
            val right = W - left
            val stepX = (right - left) / (COLS - 1)
            val stepY = W * 0.075f
            val top = W * 0.13f
            val dotR = W * 0.0065f
            // Letters grow until they nearly touch the neighbouring dots: widest glyph "M" fills the gap sideways.
            val room = 2 * (stepX - dotR) * 0.95f
            val bounds = Rect()
            val letter = Paint(p).apply {
                typeface = ctx.resources.getFont(R.font.doto_bold) // Ndot
                textSize = 100f
                textAlign = Paint.Align.CENTER
                getTextBounds("M", 0, 1, bounds)
                textSize *= minOf(room / bounds.width(), 2 * (stepY - dotR) * 0.95f / bounds.height())
            }
            for (i in 0 until COLS * ROWS) {
                val x = left + (i % COLS) * stepX
                val y = top + (i / COLS) * stepY
                val prayer = marks[i]
                val color = when {
                    prayer != null && prayer == next?.first -> RED
                    prayer != null || (day != null && i <= nowDot) -> WHITE
                    else -> DIM
                }
                when (prayer) {
                    null -> c.drawCircle(x, y, dotR, p.apply { this.color = color })
                    Prayer.SYURUK -> drawSun(c, x, y, room / 2 * 0.65f, p.apply { this.color = color })
                    else -> LETTERS.getValue(prayer).let { l ->
                        letter.getTextBounds(l, 0, 1, bounds)
                        c.drawText(l, x, y - bounds.exactCenterY(), letter.apply { this.color = color })
                    }
                }
            }

            // Bottom line, like the year widget: "1H 20M → ASAR (16:20)" on the left, zone on the right.
            val zoneText = zone?.code ?: "—"
            val time = next?.let { countdown(now, it.second).uppercase() } ?: ""
            val rest = next?.let { (np, at) -> " → ${np.label.uppercase()} (${HHMM.format(at.atZone(MYT))})" } ?: "OPEN MARI SOLAT"
            val text = Paint(letter).apply { textAlign = Paint.Align.LEFT; textSize = W * 0.068f }
            // shrink until both sides fit with a gap, e.g. "1H 59M → MAGHRIB (19:05)"
            while (text.measureText(zoneText) + text.measureText(time + rest) + W * 0.05f > right - left) text.textSize -= 1f
            val base = W * 0.87f
            c.drawText(time, left, base, text.apply { color = WHITE })
            c.drawText(rest, left + text.measureText(time), base, text.apply { color = GREY })
            c.drawText(zoneText, right - text.measureText(zoneText), base, text.apply { color = WHITE })

            val summary = next?.let { "${it.first.label} in $time" } ?: "Open Mari Solat to load prayer times"
            return bmp to summary
        }

        /** Nothing-style sun: a dot ringed by eight smaller dots. */
        private fun drawSun(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
            c.drawCircle(x, y, r * 0.42f, p)
            for (k in 0 until 8) {
                val a = Math.PI / 4 * k
                c.drawCircle(x + (r * cos(a)).toFloat(), y + (r * sin(a)).toFloat(), r * 0.13f, p)
            }
        }
    }
}
