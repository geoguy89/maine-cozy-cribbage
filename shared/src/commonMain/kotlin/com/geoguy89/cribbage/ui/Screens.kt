package com.geoguy89.cribbage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.board.BoardView
import com.geoguy89.cribbage.board.Boards
import com.geoguy89.cribbage.board.PegSet
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.Lessons
import com.geoguy89.cribbage.gfx.Wood
import com.geoguy89.cribbage.theme.BaseTheme
import com.geoguy89.cribbage.theme.Season

// ---- Title ----------------------------------------------------------------------------------------------------------

@Composable
fun TitleScreen(model: AppModel) {
    val ui = LocalUi.current
    var choosing by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mw = maxWidth
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val menu: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 380.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (model.hasSavedGame) WoodButton("Continue game", { model.resumeSolo() }, Modifier.fillMaxWidth(), primary = true)
                WoodButton("Play solo", { choosing = true }, Modifier.fillMaxWidth(), primary = !model.hasSavedGame)
                val turns = model.online.games.count { it.myMove && !it.over } + model.online.invites.count { it.incoming } + model.online.incoming.size
                WoodButton("Play with friends" + if (turns > 0) "  ($turns)" else "", { model.ensureOnline(); model.go(Screen.FRIENDS) }, Modifier.fillMaxWidth())
                WoodButton("Learn to play", { model.go(Screen.LESSONS) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WoodButton("Boards", { model.go(Screen.BOARDS) }, Modifier.weight(1f), size = 15.sp)
                    WoodButton("Settings", { model.go(Screen.SETTINGS) }, Modifier.weight(1f), size = 15.sp)
                    WoodButton("Stats", { model.go(Screen.STATS) }, Modifier.weight(1f), size = 15.sp)
                }
                model.update?.let { (v, _) ->
                    Parchment(Modifier.fillMaxWidth(), pins = false) {
                        Hand("Version $v is ready on the shelf.", size = 17.sp, weight = FontWeight.Bold)
                        val p = model.updating
                        if (p != null) Hand(if (p < 1f) "Downloading... ${(p * 100).toInt()}%" else "Installing... the game will close for a moment.", size = 15.sp)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WoodButton("Update now", { model.updateNow() }, Modifier.weight(1f), primary = true, size = 14.sp)
                            WoodButton("Later", { model.skipUpdate() }, Modifier.weight(1f), size = 14.sp)
                        }
                    }
                }
            }
        }
        val hero: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                WoodSign("Maine Cozy Cribbage", size = if (mw < 380.dp) 22.sp else 28.sp)
                Spacer(Modifier.height(8.dp))
                model.season?.let { Txt(it.greeting, size = 16.sp, color = ui.onTable, font = ui.fonts.hand, shadow = true, align = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
                BoardView(model.board, listOf(17, 9), listOf(12, 5), Modifier.fillMaxWidth(), pegs = model.settings.pegs, ambient = model.settings.ambient)
            }
        }
        if (wide) Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            hero(Modifier.weight(1.2f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { menu() }
        } else Column(Modifier.fillMaxSize().padding(16.dp).verticalScrollCompat(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            hero(Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            menu()
        }
        if (choosing) Scrim({ choosing = false }) {
            Parchment(Modifier.padding(20.dp).widthIn(max = 420.dp)) {
                Hand("Who's across the table?", size = 22.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                for (d in Difficulty.entries) {
                    WoodButton(d.title, { choosing = false; model.newSoloGame(d) }, Modifier.fillMaxWidth(), primary = d == model.settings.difficulty)
                    Hand(d.blurb, Modifier.padding(start = 6.dp, bottom = 8.dp, top = 2.dp), size = 14.sp)
                }
                if (model.hasSavedGame) Hand("Starting a new game replaces the one in progress.", size = 13.sp, color = Color(0xFF8A3A20))
            }
        }
    }
}

// ---- Lessons --------------------------------------------------------------------------------------------------------

@Composable
fun LessonsScreen(model: AppModel) {
    Page("Learn to Play", model::back) {
        Hand("Short, hands-on lessons. Start at the top, or jump to what you need. The tutorial helper also gives tips during solo games (Settings).", size = 15.sp)
        Spacer(Modifier.height(8.dp))
        for ((i, l) in Lessons.all.withIndex()) {
            val done = l.id in model.progress.lessonsDone
            Row(Modifier.fillMaxWidth().clickable { model.openLesson(l) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).drawBehind {
                    drawCircle(if (done) Color(0xFF5F8F3A) else Color(0xFF8C6A3A))
                }, contentAlignment = Alignment.Center) { Txt(if (done) "✓" else "${i + 1}", size = 15.sp, color = Color.White, weight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(l.title, size = 17.sp, weight = FontWeight.Medium)
                    Hand(l.blurb, size = 14.sp)
                }
                Txt("›", size = 24.sp)
            }
        }
    }
}

// ---- Boards ---------------------------------------------------------------------------------------------------------

@Composable
fun BoardsScreen(model: AppModel) {
    val ui = LocalUi.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 900.dp) 3 else if (maxWidth >= 560.dp) 2 else 1
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WoodButton("‹ Back", model::back, size = 14.sp)
                Spacer(Modifier.weight(1f))
            }
            WoodSign("Choose a Board", size = 24.sp)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in Boards.all.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (b in row) {
                            val chosen = b.id == model.settings.board
                            Column(Modifier.weight(1f).clickable { model.updateSettings { it.copy(board = b.id) }; model.sound(com.geoguy89.cribbage.audio.Sfx.PEG) }
                                .plank(Wood.WALNUT, b.seed, 12.dp, glow = if (chosen) ui.accent else null).padding(10.dp)) {
                                BoardView(b, listOf(23, 17), listOf(15, 11), Modifier.fillMaxWidth(), pegs = model.settings.pegs, ambient = chosen && model.settings.ambient, animatePegs = false)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Txt(b.name, size = 18.sp, color = Color(0xFFF6E7C8), weight = FontWeight.SemiBold, font = ui.fonts.title)
                                        Txt("${b.place} · ${b.wood.title}", size = 13.sp, color = Color(0xFFE0C9A0))
                                    }
                                    if (chosen) Txt("✓ In play", size = 14.sp, color = Color(0xFF9CE27C), weight = FontWeight.Bold)
                                }
                                Txt(b.blurb, size = 13.sp, color = Color(0xFFEAD9B8))
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---- Settings -------------------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(model: AppModel) {
    val s = model.settings
    Page("Settings", model::back) {
        Section("You")
        SettingRow("Your name", "Shown to friends and on the scoreboard") {}
        NameField(s.playerName) { n -> model.updateSettings { it.copy(playerName = n.take(18), nameChosen = true) } }

        Section("Sound")
        SettingRow("Music", "Soft fiddle-and-guitar tunes, seasonal ones too") { Txt("${(s.music * 100).toInt()}%", size = 14.sp) }
        WoodSlider(s.music, { v -> model.updateSettings { it.copy(music = v) } })
        SettingRow("Sound effects", "Pegs, cards and scoring") { Txt("${(s.sfx * 100).toInt()}%", size = 14.sp) }
        WoodSlider(s.sfx, { v -> model.updateSettings { it.copy(sfx = v) } })
        SettingRow("Vibration") { Toggle(s.haptics, { v -> model.updateSettings { it.copy(haptics = v) } }) }

        Section("Look")
        SettingRow("Table", s.theme.blurb) {}
        Chips(BaseTheme.entries.map { it.title }, s.theme.ordinal) { i -> model.updateSettings { it.copy(theme = BaseTheme.entries[i]) } }
        SettingRow("Seasons & holidays", "Decorations change with the calendar: snow in winter, foliage in the fall, lights at Christmas...") {
            Toggle(s.seasonal, { v -> model.updateSettings { it.copy(seasonal = v) } })
        }
        SettingRow("Show a particular one", if (s.pinnedSeason == null) "Following the calendar" else "Always ${s.pinnedSeason.title}") {}
        Chips(listOf("Calendar") + Season.entries.map { it.title }, (s.pinnedSeason?.ordinal ?: -1) + 1) { i ->
            model.updateSettings { it.copy(pinnedSeason = if (i == 0) null else Season.entries[i - 1]) }
        }
        SettingRow("Pegs") {}
        Chips(PegSet.entries.map { it.title }, s.pegs.ordinal) { i -> model.updateSettings { it.copy(pegs = PegSet.entries[i]) } }
        SettingRow("Wildlife & weather", "Jumping fish, drifting snow and leaves") { Toggle(s.ambient, { v -> model.updateSettings { it.copy(ambient = v) } }) }
        SettingRow("Board", model.board.name) { WoodButton("Choose", { model.go(Screen.BOARDS) }, size = 13.sp) }

        Section("Play")
        SettingRow("Tutorial helper", "Tips and suggested plays during solo games") { Toggle(s.coach, { v -> model.updateSettings { it.copy(coach = v) } }) }
        SettingRow("Count the show for me", "Step through hand counts automatically") { Toggle(s.autoShow, { v -> model.updateSettings { it.copy(autoShow = v) } }) }
        SettingRow("Solo opponent") {}
        Chips(Difficulty.entries.map { it.title }, s.difficulty.ordinal) { i -> model.updateSettings { it.copy(difficulty = Difficulty.entries[i]) } }

        Section("About")
        Hand("Maine Cozy Cribbage ${model.platform.versionName}. Lake shorelines © OpenStreetMap contributors. Fonts: Cinzel, Kalam, Oswald (SIL Open Font License).", size = 13.sp)
        Spacer(Modifier.height(6.dp))
        WoodButton("Check for updates", { model.checkForUpdate(model.http, quiet = false) }, size = 13.sp)
    }
}

@Composable
private fun Section(title: String) {
    Txt(title.uppercase(), Modifier.padding(top = 16.dp, bottom = 2.dp), size = 13.sp, weight = FontWeight.Bold, color = LocalUi.current.ink.copy(alpha = 0.65f))
}

/** A wrap-around row of choices. */
@Composable
fun Chips(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val ui = LocalUi.current
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(Modifier.clickable { onPick(i) }.drawBehind {
                drawRoundRect(if (on) ui.accent else Color(0x22000000), cornerRadius = CornerRadius(14.dp.toPx()))
            }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Txt(o, size = 13.sp, color = if (on) Color.White else ui.ink, weight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        text, { text = it.take(18); onChange(text) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 18.sp, color = ui.ink, fontFamily = ui.fonts.hand),
        cursorBrush = SolidColor(ui.ink),
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawLine(ui.ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f)
        }.padding(vertical = 6.dp),
        decorationBox = { inner -> if (text.isEmpty()) Hand("Type your name", size = 18.sp, color = ui.ink.copy(alpha = 0.4f)); inner() },
    )
}

// ---- Stats & rules ---------------------------------------------------------------------------------------------------

@Composable
fun StatsScreen(model: AppModel) {
    val st = model.stats
    Page("Your Stats", model::back) {
        Section("Solo games")
        StatLine("Games played", "${st.played}")
        StatLine("Won", "${st.won}" + if (st.played > 0) "  (${st.won * 100 / st.played}%)" else "")
        for (d in Difficulty.entries) StatLine("  vs ${d.title}", "${st.wonByLevel[d] ?: 0} of ${st.playedByLevel[d] ?: 0}")
        StatLine("Skunks given", "${st.skunksGiven}")
        StatLine("Skunks taken", "${st.skunksTaken}")
        Section("Hands")
        StatLine("Best hand", "${st.bestHand}")
        StatLine("Best crib", "${st.bestCrib}")
        StatLine("Average hand", st.averageHand)
        Section("Practice")
        StatLine("Hands counted right", "${st.drillRight} of ${st.drillTried}")
        model.online.me?.let { me ->
            Section("Online")
            StatLine("Wins against friends", "${me.wins}")
            StatLine("Losses", "${me.losses}")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Hand(label, Modifier.weight(1f), size = 16.sp)
        Txt(value, size = 16.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
fun RulesScreen(model: AppModel) {
    Page("How to Play", model::back) { RulesText() }
}
