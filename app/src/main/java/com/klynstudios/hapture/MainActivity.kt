package com.klynstudios.hapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.klynstudios.hapture.ui.design.LocalReduceMotion
import com.klynstudios.hapture.ui.design.isMotionReduced
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.feature.editor.CardExpandScreen
import com.klynstudios.hapture.data.BackdropEntity
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.feature.common.LocalOpenCompare
import com.klynstudios.hapture.feature.editor.PredictiveBackScreen
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import com.klynstudios.hapture.feature.editor.BackdropHost
import com.klynstudios.hapture.data.TokenSetWithItems
import com.klynstudios.hapture.data.spring
import com.klynstudios.hapture.export.ExportStamp
import com.klynstudios.hapture.feature.home.FeelSetsUi
import com.klynstudios.hapture.data.MotionTokenEntity
import com.klynstudios.hapture.feature.common.LocalMotionTokens
import com.klynstudios.hapture.feature.common.LocalSaveMotionToken
import androidx.compose.runtime.CompositionLocalProvider
import com.klynstudios.hapture.data.DoodleEntity
import com.klynstudios.hapture.data.ProjectFile
import com.klynstudios.hapture.data.ThemeStore
import com.klynstudios.hapture.data.TimelineEntity
import com.klynstudios.hapture.feature.editor.BottomSheetScreen
import com.klynstudios.hapture.feature.editor.DragReorderScreen
import com.klynstudios.hapture.feature.editor.MagneticSnapScreen
import com.klynstudios.hapture.feature.editor.PinchZoomScreen
import com.klynstudios.hapture.feature.editor.PullRefreshScreen
import com.klynstudios.hapture.feature.editor.SpringDragScreen
import com.klynstudios.hapture.feature.editor.TriggerScreen
import com.klynstudios.hapture.core.spec.TriggerKind
import com.klynstudios.hapture.feature.editor.SwipeFlingScreen
import com.klynstudios.hapture.feature.doodle.DoodleScreen
import com.klynstudios.hapture.feature.capture.CaptureScreen
import com.klynstudios.hapture.feature.compare.CompareScreen
import com.klynstudios.hapture.data.CompareStore
import com.klynstudios.hapture.data.IntroStore
import com.klynstudios.hapture.feature.intro.IntroScreen
import com.klynstudios.hapture.feature.home.HomeScreen
import com.klynstudios.hapture.feature.home.NewExperimentScreen
import com.klynstudios.hapture.feature.timeline.TimelineEditorScreen
import com.klynstudios.hapture.ui.design.DesignThemes
import com.klynstudios.hapture.ui.theme.HaptureTheme
import com.klynstudios.hapture.ui.theme.defaultThemeId
import kotlinx.coroutines.launch

/**
 * Opening a screen slides it in a little from the right while it fades in, and going back reverses that.
 * Small on purpose (a tenth of the width) and driven by a spring; with Reduce Motion it is a plain cut.
 */
private fun screenTransition(backward: Boolean, reduce: Boolean): ContentTransform {
    if (reduce) return EnterTransition.None togetherWith ExitTransition.None
    val dir = if (backward) -1 else 1
    val slide = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
    return (fadeIn(tween(200)) + slideInHorizontally(slide) { dir * it / 10 }) togetherWith
        (fadeOut(tween(120)) + slideOutHorizontally(slide) { -dir * it / 10 })
}

/**
 * Navigation is a sealed class in a mutableStateOf rather than Navigation-Compose.
 * There are only a few screens and the back stack is at most two deep (Compare
 * remembers the editor that opened it), which doesn't justify the dependency.
 */
private sealed interface Route {
    data object Home : Route
    data object NewExperiment : Route
    data class Experiment(val id: Long, val type: ExperimentType) : Route
    data class Timeline(val id: Long) : Route
    data class Doodle(val id: Long) : Route
    data object Capture : Route
    data object Intro : Route
    /** [seed] fills lane A (from an editor's "Compare with"); Back returns to [back]. */
    data class Compare(val seedName: String? = null, val seed: SpringSpec? = null, val back: Route = Home) : Route
}

/**
 * Keeps the open screen across activity recreation and process death. A route is stored as a nested list of
 * plain values (ids, enum names, floats), which a Bundle can hold; Compare's seed spring is just its three
 * numbers. Anything that doesn't decode (say, a saved type name this build no longer has) lands on Home.
 */
private val RouteSaver: Saver<Route, Any> = Saver(
    save = { encodeRoute(it) },
    restore = { saved -> runCatching { decodeRoute(saved as List<*>) }.getOrNull() ?: Route.Home },
)

private fun encodeRoute(route: Route): ArrayList<Any?> = when (route) {
    Route.Home -> arrayListOf("home")
    Route.NewExperiment -> arrayListOf("new")
    is Route.Experiment -> arrayListOf("experiment", route.id, route.type.name)
    is Route.Timeline -> arrayListOf("timeline", route.id)
    is Route.Doodle -> arrayListOf("doodle", route.id)
    Route.Capture -> arrayListOf("capture")
    Route.Intro -> arrayListOf("intro")
    is Route.Compare -> arrayListOf(
        "compare", route.seedName, route.seed?.stiffness, route.seed?.dampingRatio, route.seed?.mass, encodeRoute(route.back),
    )
}

private fun decodeRoute(saved: List<*>): Route = when (saved[0]) {
    "home" -> Route.Home
    "new" -> Route.NewExperiment
    "experiment" -> Route.Experiment(saved[1] as Long, ExperimentType.valueOf(saved[2] as String))
    "timeline" -> Route.Timeline(saved[1] as Long)
    "doodle" -> Route.Doodle(saved[1] as Long)
    "capture" -> Route.Capture
    "intro" -> Route.Intro
    "compare" -> Route.Compare(
        seedName = saved[1] as String?,
        seed = (saved[2] as Float?)?.let { k -> SpringSpec(k, saved[3] as Float, saved[4] as Float) },
        back = decodeRoute(saved[5] as List<*>),
    )
    else -> Route.Home
}

/** Where Back goes from [route]: Compare returns to whoever opened it, everything else to Home. */
private fun parentOf(route: Route): Route = if (route is Route.Compare) route.back else Route.Home

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = haptureApp
        val repository = app.experiments
        val timelineRepository = app.timelines
        val doodleRepository = app.doodles
        val tokenRepository = app.tokens
        val tokenSetRepository = app.tokenSets
        val backdropRepository = app.backdrops
        val themeStore = ThemeStore(applicationContext)
        val compareStore = CompareStore(applicationContext)
        val introStore = IntroStore(applicationContext)

        setContent {
            val reduceMotion = remember {
                isMotionReduced(android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
            }
            var picked by remember { mutableStateOf(themeStore.load()) }
            val themeId = picked ?: defaultThemeId()

            // System bar icons follow the *theme*, not the phone's light/dark
            // setting -- chalk on a light phone would otherwise get dark icons
            // on a dark page.
            val dark = DesignThemes.of(themeId).dark
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }

            HaptureTheme(themeId) {
                // A first-time visitor lands on the intro; everyone else goes straight to Home.
                var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<Route>(if (introStore.seen()) Route.Home else Route.Intro) }
                val scope = rememberCoroutineScope()
                val tokens by tokenRepository.observeAll().collectAsState(initial = emptyList<MotionTokenEntity>())

                // Screenshot rows of experiments deleted this session, so Undo can put them back too (the row
                // cascades away with the experiment; the image file stays on disk until the next delete of that id).
                val deletedBackdrops = remember { mutableMapOf<Long, BackdropEntity>() }

                // In-app predictive back: while the system back gesture is held, the screen shrinks and leans toward
                // the finger, the same shape as the system's own; letting go either goes back or springs into place.
                val backProgress = remember { Animatable(0f) }
                var backFromLeft by remember { mutableStateOf(true) }
                var swiped by remember { mutableStateOf<Route?>(null) }

                CompositionLocalProvider(
                    LocalReduceMotion provides reduceMotion,
                    LocalMotionTokens provides tokens,
                    LocalSaveMotionToken provides { n, k, z -> scope.launch { tokenRepository.save(n, k, z) } },
                    LocalOpenCompare provides { n, spring -> route = Route.Compare(n, spring, back = route) },
                ) {
                // The intro handles back itself (it steps back a page first).
                PredictiveBackHandler(enabled = route !is Route.Home && route !is Route.Intro) { events ->
                    val from = route
                    swiped = from
                    try {
                        events.collect { e ->
                            backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                            if (!reduceMotion) backProgress.snapTo(e.progress)
                        }
                        route = parentOf(from)
                        // Keep the outgoing screen as it was let go while it fades, then reset for the next swipe.
                        scope.launch {
                            kotlinx.coroutines.delay(320)
                            backProgress.snapTo(0f)
                            swiped = null
                        }
                    } catch (e: CancellationException) {
                        scope.launch {
                            backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            swiped = null
                        }
                        throw e
                    }
                }

                val canvas = com.klynstudios.hapture.ui.design.LocalTokens.current.canvas
                val line = com.klynstudios.hapture.ui.design.LocalTokens.current.line
                // The canvas colour sits behind the transition, or the fade and slide would show the black window underneath.
                // It darkens a touch under a back swipe, so the shrinking screen has an edge to read against.
                AnimatedContent(
                    modifier = Modifier.fillMaxSize().background(lerp(canvas, line, backProgress.value)),
                    targetState = route,
                    transitionSpec = { screenTransition(backward = targetState == parentOf(initialState), reduce = reduceMotion) },
                    label = "screen",
                ) { current ->
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().then(
                        if (current == swiped) Modifier.graphicsLayer {
                            val p = backProgress.value
                            val s = 1f - 0.1f * p
                            scaleX = s
                            scaleY = s
                            translationX = (if (backFromLeft) 1f else -1f) * size.width * 0.04f * p
                            shape = RoundedCornerShape(28.dp.toPx() * (p * 5f).coerceAtMost(1f))
                            clip = true
                        } else Modifier
                    ),
                ) {
                when (current) {
                    is Route.Home -> {
                        val experiments by repository.observeAll()
                            .collectAsState(initial = emptyList<ExperimentEntity>())
                        val doodles by doodleRepository.observeAll()
                            .collectAsState(initial = emptyList<DoodleEntity>())
                        val timelines by timelineRepository.observeAll()
                            .collectAsState(initial = emptyList<TimelineEntity>())
                        val setRows by tokenSetRepository.observeSets().collectAsState(initial = emptyList())
                        val setItems by tokenSetRepository.observeItems().collectAsState(initial = emptyList())
                        val feelSets = FeelSetsUi(
                            sets = setRows.map { s -> TokenSetWithItems(s, setItems.filter { it.setId == s.id }) },
                            tokens = tokens,
                            currentSprings = tokens.map { ExportStamp.Spring("t", it.name, it.stiffness, it.dampingRatio) } +
                                experiments.map { e -> e.spring().let { ExportStamp.Spring("x", e.name, it.stiffness, it.dampingRatio) } },
                            onApply = { springs -> scope.launch { springs.forEach { tokenRepository.save(it.name, it.stiffness, it.dampingRatio) } } },
                            onSaveSet = { n, a -> scope.launch { tokenSetRepository.create(n, a, tokens.map { Triple(it.name, it.stiffness, it.dampingRatio) }) } },
                            onDeleteSet = { s -> scope.launch { tokenSetRepository.delete(s.set) } },
                        )
                        HomeScreen(
                            feelSets = feelSets,
                            recent = experiments,
                            timelines = timelines,
                            themeId = themeId,
                            onTheme = { picked = it; themeStore.save(it) },
                            onNewExperiment = { route = Route.NewExperiment },
                            onOpen = { route = Route.Experiment(it.id, it.type) },
                            onRename = { entity, newName -> scope.launch { repository.rename(entity, newName) } },
                            onDelete = { entity ->
                                scope.launch {
                                    backdropRepository.snapshot(entity.id)?.let { deletedBackdrops[entity.id] = it }
                                    repository.delete(entity)
                                }
                            },
                            onUndoDelete = { entity ->
                                scope.launch {
                                    repository.restore(entity)
                                    deletedBackdrops.remove(entity.id)?.let { backdropRepository.restore(it) }
                                }
                            },
                            onPin = { entity, pinned -> scope.launch { repository.setPinned(entity, pinned) } },
                            onImport = { text ->
                                when (val result = ProjectFile.decode(text)) {
                                    is ProjectFile.Result.Ok -> {
                                        val saved = repository.importEntity(result.entity)
                                        "Imported \u201c${saved.name}\u201d"
                                    }
                                    is ProjectFile.Result.Error -> result.reason
                                }
                            },
                            onNewTimeline = {
                                scope.launch {
                                    val entity = timelineRepository.createDefault()
                                    route = Route.Timeline(entity.id)
                                }
                            },
                            onOpenTimeline = { route = Route.Timeline(it.id) },
                            onRenameTimeline = { entity, newName -> scope.launch { timelineRepository.rename(entity, newName) } },
                            onDeleteTimeline = { entity -> scope.launch { timelineRepository.delete(entity) } },
                            doodles = doodles,
                            onNewDoodle = {
                                scope.launch {
                                    val entity = doodleRepository.createDefault()
                                    route = Route.Doodle(entity.id)
                                }
                            },
                            onOpenDoodle = { route = Route.Doodle(it.id) },
                            onRenameDoodle = { entity, newName -> scope.launch { doodleRepository.rename(entity, newName) } },
                            onDeleteDoodle = { entity -> scope.launch { doodleRepository.delete(entity) } },
                            onUndoDeleteDoodle = { entity -> scope.launch { doodleRepository.restore(entity) } },
                            tokens = tokens,
                            onCapture = { route = Route.Capture },
                            onCompare = { route = Route.Compare() },
                            onHowItWorks = { route = Route.Intro },
                            onDeleteToken = { token -> scope.launch { tokenRepository.delete(token) } },
                        )
                    }

                    is Route.NewExperiment -> NewExperimentScreen(
                        onBack = { route = Route.Home },
                        onPick = { type ->
                            scope.launch {
                                val entity = repository.createDefault(type)
                                route = Route.Experiment(entity.id, entity.type)
                            }
                        },
                    )

                    is Route.Experiment -> BackdropHost(current.id, backdropRepository) { when (current.type) {
                        ExperimentType.SPRING_DRAG -> SpringDragScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.MAGNETIC_SNAP -> MagneticSnapScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.SWIPE_FLING -> SwipeFlingScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.BOTTOM_SHEET -> BottomSheetScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.PULL_REFRESH -> PullRefreshScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.PINCH_ZOOM -> PinchZoomScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.CARD_EXPAND -> CardExpandScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.PREDICTIVE_BACK -> PredictiveBackScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.DRAG_REORDER -> DragReorderScreen(
                            experimentId = current.id,
                            repository = repository,
                            onBack = { route = Route.Home },
                        )

                        ExperimentType.TOGGLE, ExperimentType.BUTTON_PRESS, ExperimentType.TAB_INDICATOR,
                        ExperimentType.STAGGER_LIST, ExperimentType.LIKE_BURST -> TriggerScreen(
                            experimentId = current.id,
                            kind = when (current.type) {
                                ExperimentType.TOGGLE -> TriggerKind.TOGGLE
                                ExperimentType.BUTTON_PRESS -> TriggerKind.BUTTON_PRESS
                                ExperimentType.TAB_INDICATOR -> TriggerKind.TAB_INDICATOR
                                ExperimentType.STAGGER_LIST -> TriggerKind.STAGGER_LIST
                                else -> TriggerKind.LIKE_BURST
                            },
                            repository = repository,
                            onBack = { route = Route.Home },
                        )
                    }
                    }

                    is Route.Intro -> IntroScreen(onDone = {
                        introStore.markSeen()
                        route = Route.Home
                    })

                    is Route.Compare -> CompareScreen(
                        store = compareStore,
                        seedName = current.seedName,
                        seed = current.seed,
                        onBack = { route = current.back },
                    )

                    is Route.Capture -> CaptureScreen(
                        onBack = { route = Route.Home },
                        onCreateStagger = { k, z, ms ->
                            scope.launch {
                                val e = repository.createDefault(ExperimentType.STAGGER_LIST)
                                val saved = e.copy(
                                    name = "Captured Stagger",
                                    stiffnessT = com.klynstudios.hapture.core.physics.ParameterMapping.stiffnessT(k),
                                    dampingT = com.klynstudios.hapture.core.physics.ParameterMapping.dampingT(z),
                                    triggerStaggerT = com.klynstudios.hapture.core.spec.TriggerSpecs.staggerT(ms),
                                )
                                repository.save(saved)
                                route = Route.Experiment(saved.id, saved.type)
                            }
                        },
                        onCreate = { k, z ->
                            scope.launch {
                                val e = repository.createDefault(ExperimentType.TOGGLE)
                                val saved = e.copy(
                                    name = "Captured Feel",
                                    stiffnessT = com.klynstudios.hapture.core.physics.ParameterMapping.stiffnessT(k),
                                    dampingT = com.klynstudios.hapture.core.physics.ParameterMapping.dampingT(z),
                                )
                                repository.save(saved)
                                route = Route.Experiment(saved.id, saved.type)
                            }
                        },
                    )

                    is Route.Doodle -> DoodleScreen(
                        doodleId = current.id,
                        repository = doodleRepository,
                        onBack = { route = Route.Home },
                    )

                    is Route.Timeline -> TimelineEditorScreen(
                        timelineId = current.id,
                        timelineRepository = timelineRepository,
                        experimentRepository = repository,
                        onBack = { route = Route.Home },
                    )
                }
                }
                }
                }
            }
        }
    }
}
