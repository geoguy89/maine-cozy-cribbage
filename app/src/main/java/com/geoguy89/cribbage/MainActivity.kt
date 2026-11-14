package com.geoguy89.cribbage

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.geoguy89.cribbage.audio.AudioEngine
import com.geoguy89.cribbage.data.PrefsKeyValueStore
import com.geoguy89.cribbage.data.Store
import com.geoguy89.cribbage.net.OkHttpTransport
import com.geoguy89.cribbage.ui.AppModel
import com.geoguy89.cribbage.ui.CribbageApp
import com.geoguy89.cribbage.ui.Haptic
import com.geoguy89.cribbage.ui.Platform
import com.geoguy89.cribbage.ui.Posture
import com.geoguy89.cribbage.ui.Screen
import kotlinx.coroutines.flow.map

/** Keeps the game alive through configuration changes such as folding and unfolding. */
class GameHolder(app: Application) : AndroidViewModel(app) {
    val platform = AndroidPlatform(app)
    private val http = OkHttpTransport()
    val model = AppModel(Store(PrefsKeyValueStore(app)), AudioEngine(app), http, platform, viewModelScope)

    init {
        model.checkForUpdate(http)
        TurnWorker.schedule(app)
    }

    override fun onCleared() = model.audio.release()
}

class MainActivity : ComponentActivity() {
    private val holder: GameHolder by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = holder.model
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        holder.platform.askPermission = {
            if (Build.VERSION.SDK_INT >= 33) runOnUiThread { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        holder.platform.activity = this
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { TurnWorker.foreground = true; model.onForeground() }
                Lifecycle.Event.ON_STOP -> { TurnWorker.foreground = false; model.onBackground() }
                else -> Unit
            }
        })
        // A notification tap opens the Friends screen.
        if (intent?.getBooleanExtra(TurnWorker.EXTRA_FRIENDS, false) == true) model.screen = Screen.FRIENDS

        // Foldables: follow the hinge, so nothing straddles the fold when the phone is half open.
        val folds = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.takeIf { it.state == FoldingFeature.State.HALF_OPENED }
        }

        setContent {
            val fold by folds.collectAsState(initial = null)
            val density = LocalDensity.current
            val posture = fold?.let { f ->
                with(density) {
                    if (f.orientation == FoldingFeature.Orientation.HORIZONTAL) Posture(tabletopHingeY = f.bounds.top.toDp().value, hingeThickness = f.bounds.height().toDp().value)
                    else Posture(bookHingeX = f.bounds.left.toDp().value, hingeThickness = f.bounds.width().toDp().value)
                }
            } ?: Posture()
            val view = LocalView.current
            holder.platform.view = view
            // Keep the screen awake during a game.
            val playing = model.screen == Screen.GAME
            SideEffect { view.keepScreenOn = playing }
            BackHandler(enabled = model.screen != Screen.TITLE) { model.back() }
            CribbageApp(model, posture)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(TurnWorker.EXTRA_FRIENDS, false)) holder.model.go(Screen.FRIENDS)
    }
}

class AndroidPlatform(private val app: Application) : Platform {
    var askPermission: () -> Unit = {}
    var view: View? = null
    var activity: ComponentActivity? = null

    override val versionName: String = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    override val versionCode: Int = app.packageManager.getPackageInfo(app.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
    }

    override fun haptic(kind: Haptic) {
        val c = when (kind) {
            Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Haptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        view?.performHapticFeedback(c)
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        (activity ?: return).startActivity(Intent.createChooser(send, "Share your friend code"))
    }

    override fun openUrl(url: String) {
        (activity ?: return).startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun askNotificationPermission() = askPermission()

    override fun installUpdate(apkUrl: String, progress: (Float) -> Unit, done: (String?) -> Unit): Boolean {
        Updater.install(app, apkUrl, progress, done)
        return true
    }
}
