package com.geoguy89.cribbage.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The whole game: the table and its decorations, with the current screen on top. */
@Composable
fun CribbageApp(model: AppModel, posture: Posture = Posture()) {
    ProvideUi(model.look) {
        Scenery(model.look, animate = model.settings.ambient) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AnimatedContent(model.screen, transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.03f)) }, label = "screen") { screen ->
                    when (screen) {
                        Screen.TITLE -> TitleScreen(model)
                        Screen.GAME -> model.table?.let { GameScreen(model, it, posture) } ?: TitleScreen(model)
                        Screen.LESSONS -> LessonsScreen(model)
                        Screen.LESSON -> model.lesson?.let { LessonScreen(model, it) } ?: LessonsScreen(model)
                        Screen.BOARDS -> BoardsScreen(model)
                        Screen.SETTINGS -> SettingsScreen(model)
                        Screen.FRIENDS -> FriendsScreen(model)
                        Screen.STATS -> StatsScreen(model)
                        Screen.RULES -> RulesScreen(model)
                    }
                }
                model.toast?.let { t ->
                    LaunchedEffect(t) { delay(3500); model.toast = null }
                    Toast(t, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
                }
            }
        }
    }
}
