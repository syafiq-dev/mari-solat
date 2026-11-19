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
import dev.syafiq.solat.data.nextTick
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home-screen widget: the day as 144 dots (10 minutes each, 16 x 9), lit up to now.
 * Each prayer's dot is replaced by its letter (S, sun, Z, A, M, I); the current one is red, the rest white.
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
            val now = Instant.now()
            val (bitmap, summary, prayerAt) = render(ctx, now)
            val views = RemoteViews(ctx.packageName, R.layout.widget_prayer).apply {
                setImageViewBitmap(R.id.widget_image, bitmap)
                setContentDescription(R.id.widget_image, summary)
                setOnClickPendingIntent(
                    R.id.widget_image,
                    PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            mgr.updateAppWidget(ids, views)
            // Non-wakeup alarm on each 10-minute dot (or the prayer itself, if sooner): it waits while the screen is off.
            // Every minute was too often: Android's standby limits (~10 alarms/hour) then skipped 20-30 minutes.
            ctx.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC, nextTick(now, prayerAt).toEpochMilli(), 30_000, tick(ctx))
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

        private fun render(ctx: Context, now: Instant): Triple<Bitmap, String, Instant?> {
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
            // the prayer whose time we're in now; before Subuh that's still Isyak, so today's I stays red
            val current = day?.let { d -> COUNTDOWN.lastOrNull { !d.times.getValue(it).isAfter(now) } ?: Prayer.ISHA }
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
            val top = W * 0.16f
            val dotR = W * 0.0065f
            // Space between the neighbouring dots; letters take half of it, the sun a little more.
            val room = 2 * (stepX - dotR) * 0.95f
            val bounds = Rect()
            val letter = Paint(p).apply {
                typeface = ctx.resources.getFont(R.font.doto_bold) // Ndot
                textSize = 100f
                textAlign = Paint.Align.CENTER
                getTextBounds("M", 0, 1, bounds)
                textSize *= minOf(room / 2 / bounds.width(), (stepY - dotR) * 0.95f / bounds.height()) // half the gap, so prayers in neighbouring dots (e.g. A above M) never overlap
            }
            for (i in 0 until COLS * ROWS) {
                val x = left + (i % COLS) * stepX
                val y = top + (i / COLS) * stepY
                val prayer = marks[i]
                val color = when {
                    prayer != null && prayer == current -> RED
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

            // Bottom line, like the year widget: "1H 20M → ASAR (16:20)"; the zone sits small, top left.
            val zoneText = zone?.code ?: "—"
            val time = next?.let { countdown(now, it.second).uppercase() } ?: ""
            // grey line with the next prayer's name in white: " → " ASAR " (16:20)"
            val parts = next?.let { (np, at) -> listOf(" → " to GREY, np.label.uppercase() to WHITE, " (${HHMM.format(at.atZone(MYT))})" to GREY) }
                ?: listOf("OPEN MARI SOLAT" to GREY)
            val rest = parts.joinToString("") { it.first }
            val tl = left - dotR // text ink lines up with the outer edges of the first and last dot columns
            val tr = right + dotR
            val text = Paint(letter).apply { textAlign = Paint.Align.LEFT; textSize = W * 0.068f }
            // shrink until it fits (sized as if the zone still shared the line, which looked right), e.g. "1H 59M → MAGHRIB (19:05)"
            while (text.measureText(zoneText) + text.measureText(time + rest) + W * 0.05f > tr - tl) text.textSize -= 1f
            val base = W * 0.90f
            val start = time + rest
            text.getTextBounds(start, 0, start.length, bounds)
            val x0 = tl - bounds.left // drop the glyph's side bearing so the ink, not the advance box, touches the edge
            c.drawText(time, x0, base, text.apply { color = WHITE })
            var x = x0 + text.measureText(time)
            for ((str, col) in parts) {
                c.drawText(str, x, base, text.apply { color = col })
                x += text.measureText(str)
            }
            text.textSize = W * 0.06f
            text.getTextBounds(zoneText, 0, zoneText.length, bounds)
            c.drawText(zoneText, tl - bounds.left, W * 0.095f, text.apply { color = GREY })

            val summary = next?.let { "${it.first.label} in $time" } ?: "Open Mari Solat to load prayer times"
            return Triple(bmp, summary, next?.second)
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
