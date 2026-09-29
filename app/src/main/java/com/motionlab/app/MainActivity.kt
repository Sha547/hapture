package com.motionlab.app

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
import com.motionlab.app.ui.design.LocalReduceMotion
import com.motionlab.app.ui.design.isMotionReduced
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.data.ExperimentType
import com.motionlab.app.data.MIGRATION_1_2
import com.motionlab.app.data.MIGRATION_2_3
import com.motionlab.app.data.MIGRATION_3_4
import com.motionlab.app.data.MIGRATION_4_5
import com.motionlab.app.data.MIGRATION_5_6
import com.motionlab.app.data.MIGRATION_6_7
import com.motionlab.app.data.MIGRATION_7_8
import com.motionlab.app.data.MIGRATION_8_9
import com.motionlab.app.data.MIGRATION_9_10
import com.motionlab.app.data.MIGRATION_10_11
import com.motionlab.app.data.MIGRATION_11_12
import com.motionlab.app.data.MIGRATION_12_13
import com.motionlab.app.data.MIGRATION_13_14
import com.motionlab.app.data.BackdropEntity
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.feature.common.LocalOpenCompare
import com.motionlab.app.feature.editor.PredictiveBackScreen
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import com.motionlab.app.data.MotionTokenRepository
import com.motionlab.app.data.BackdropRepository
import com.motionlab.app.data.TokenSetRepository
import com.motionlab.app.feature.editor.BackdropHost
import com.motionlab.app.data.TokenSetWithItems
import com.motionlab.app.data.spring
import com.motionlab.app.export.ExportStamp
import com.motionlab.app.feature.home.FeelSetsUi
import com.motionlab.app.data.MotionTokenEntity
import com.motionlab.app.feature.common.LocalMotionTokens
import com.motionlab.app.feature.common.LocalSaveMotionToken
import androidx.compose.runtime.CompositionLocalProvider
import com.motionlab.app.data.DoodleRepository
import com.motionlab.app.data.DoodleEntity
import com.motionlab.app.data.MotionLabDatabase
import com.motionlab.app.data.ProjectFile
import com.motionlab.app.data.ThemeStore
import com.motionlab.app.data.TimelineEntity
import com.motionlab.app.data.TimelineRepository
import com.motionlab.app.feature.editor.BottomSheetScreen
import com.motionlab.app.feature.editor.DragReorderScreen
import com.motionlab.app.feature.editor.MagneticSnapScreen
import com.motionlab.app.feature.editor.PinchZoomScreen
import com.motionlab.app.feature.editor.PullRefreshScreen
import com.motionlab.app.feature.editor.SpringDragScreen
import com.motionlab.app.feature.editor.TriggerScreen
import com.motionlab.app.core.spec.TriggerKind
import com.motionlab.app.feature.editor.SwipeFlingScreen
import com.motionlab.app.feature.doodle.DoodleScreen
import com.motionlab.app.feature.capture.CaptureScreen
import com.motionlab.app.feature.compare.CompareScreen
import com.motionlab.app.data.CompareStore
import com.motionlab.app.feature.home.HomeScreen
import com.motionlab.app.feature.home.NewExperimentScreen
import com.motionlab.app.feature.timeline.TimelineEditorScreen
import com.motionlab.app.ui.design.DesignThemes
import com.motionlab.app.ui.theme.MotionLabTheme
import com.motionlab.app.ui.theme.defaultThemeId
import kotlinx.coroutines.launch

/**
 * No account, no cloud, no onboarding wall (spec §32). Opening the app
 * drops straight into Home, backed by a local Room database (spec §33) --
 * experiments persist across launches.
 *
 * Navigation is a plain sealed-class + mutableStateOf switch, not the
 * Navigation-Compose library -- a handful of screens and no back stack deeper
 * than one level (everything's onBack goes straight to Home) doesn't earn
 * that dependency yet. A Timeline (spec §26, grouping multiple experiments)
 * is the first thing that references another saved item rather than just
 * holding its own state; still doesn't need real deep-linkable routes.
 */
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

private sealed interface Route {
    data object Home : Route
    data object NewExperiment : Route
    data class Experiment(val id: Long, val type: ExperimentType) : Route
    data class Timeline(val id: Long) : Route
    data class Doodle(val id: Long) : Route
    data object Capture : Route
    /** [seed] fills lane A (from an editor's "Compare with"); Back returns to [back]. */
    data class Compare(val seedName: String? = null, val seed: SpringSpec? = null, val back: Route = Home) : Route
}

/** Where Back goes from [route]: Compare returns to whoever opened it, everything else to Home. */
private fun parentOf(route: Route): Route = if (route is Route.Compare) route.back else Route.Home

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = Room.databaseBuilder(
            applicationContext,
            MotionLabDatabase::class.java,
            "motion-lab.db",
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
        ).build()
        val repository = ExperimentRepository(database.experimentDao())
        val timelineRepository = TimelineRepository(database.timelineDao())
        val doodleRepository = DoodleRepository(database.doodleDao())
        val tokenRepository = MotionTokenRepository(database.motionTokenDao())
        val tokenSetRepository = TokenSetRepository(database.tokenSetDao())
        val backdropRepository = BackdropRepository(applicationContext, database.backdropDao())
        val themeStore = ThemeStore(applicationContext)
        val compareStore = CompareStore(applicationContext)

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

            MotionLabTheme(themeId) {
                var route by remember { mutableStateOf<Route>(Route.Home) }
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
                PredictiveBackHandler(enabled = route !is Route.Home) { events ->
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

                val canvas = com.motionlab.app.ui.design.LocalTokens.current.canvas
                val line = com.motionlab.app.ui.design.LocalTokens.current.line
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
                                    stiffnessT = com.motionlab.app.core.physics.ParameterMapping.stiffnessT(k),
                                    dampingT = com.motionlab.app.core.physics.ParameterMapping.dampingT(z),
                                    triggerStaggerT = com.motionlab.app.core.spec.TriggerSpecs.staggerT(ms),
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
                                    stiffnessT = com.motionlab.app.core.physics.ParameterMapping.stiffnessT(k),
                                    dampingT = com.motionlab.app.core.physics.ParameterMapping.dampingT(z),
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
