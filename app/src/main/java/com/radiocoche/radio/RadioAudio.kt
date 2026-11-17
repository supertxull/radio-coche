package com.radiocoche.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.radiocoche.radio.tuner.TopWayTuner

/**
 * Silencio de la radio durante los avisos de radar del launcher.
 *
 * El sonido de la radio no pasa por Android (va del sintonizador al procesador de sonido), así que
 * Android no puede bajarlo. Por eso la Radio pide el «foco de sonido» de Android cuando suena:
 * - si otra app lo pide un momento (el pitido del avisador), la Radio se calla y vuelve al terminar;
 * - si otra app lo pide del todo (Música, Spotify…), la Radio deja de estar activa y ya no hace nada.
 *
 * Si la app Radio está cerrada (la radio sigue sonando), el launcher envía "mute" / "unmute"
 * por [LauncherCommandReceiver] y se hace lo mismo con el último estado conocido.
 */
object RadioAudio {
    private const val PREFS = "radio_audio"
    private const val ACTIVE = "active"

    private val main = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private var appCtx: Context? = null
    private var silenced = false

    /** Lo pone la pantalla de la Radio mientras existe: usa su propio sintonizador. */
    @Volatile
    var live: ((on: Boolean) -> Unit)? = null

    /** Esta app tiene ahora mismo el foco de sonido y lo gestiona ella. */
    val holdsFocus: Boolean get() = focus != null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val ctx = appCtx ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> silence(ctx, true)
            AudioManager.AUDIOFOCUS_GAIN -> silence(ctx, false)
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Otra app se ha quedado con el sonido: la radio ya no es la fuente.
                if (silenced) silenced = false
                setActive(ctx, false)
                release(ctx)
            }
        }
    }

    /** La radio pasa a sonar (la pantalla de la Radio pide su audio). */
    fun take(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        setActive(app, true)
        if (focus != null || Build.VERSION.SDK_INT < 26) return
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, main)
            .build()
        if (runCatching { am.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focus = req
        }
    }

    private fun release(ctx: Context) {
        val req = focus ?: return
        focus = null
        if (Build.VERSION.SDK_INT >= 26) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching { am.abandonAudioFocusRequest(req) }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun setActive(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(ACTIVE, on).apply()

    fun isActive(ctx: Context): Boolean = prefs(ctx).getBoolean(ACTIVE, false)

    /** Orden "mute" / "unmute" del launcher con la app cerrada o sin foco. */
    fun fromLauncher(ctx: Context, mute: Boolean) {
        if (holdsFocus) return // ya lo resuelve el foco de sonido
        if (!isActive(ctx)) return
        silence(ctx, mute, force = true)
    }

    private fun silence(ctx: Context, on: Boolean, force: Boolean = false) {
        if (!force && on == silenced) return
        silenced = on
        live?.let { it(!on); return }
        val tuner = TopWayTuner.create() ?: return
        try {
            tuner.setRadioAudio(!on)
        } finally {
            tuner.release()
        }
    }
}
