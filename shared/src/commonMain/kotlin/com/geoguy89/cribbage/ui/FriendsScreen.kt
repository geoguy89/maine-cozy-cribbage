package com.geoguy89.cribbage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.net.GameEntry

/**
 * Friends and games, Refiner's Fire style: share your friend code, add friends by theirs, invite them to a game,
 * and play each game live or turn by turn.
 */
@Composable
fun FriendsScreen(model: AppModel) {
    val on = model.online
    val ui = LocalUi.current
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    Page("Play with Friends", model::back, actions = { WoodButton("Refresh", { on.sync() }, size = 13.sp) }) {
        val acc = on.account
        if (acc == null) {
            Hand(if (on.reachable) "Setting up your table online..." else "Can't reach the game server. Check your connection and try again.", size = 17.sp)
            Spacer(Modifier.height(8.dp))
            WoodButton("Try again", { model.ensureOnline() }, primary = true)
            return@Page
        }
        if (!model.settings.nameChosen) {
            Hand("What should friends call you?", size = 17.sp, weight = FontWeight.Bold)
            NameField(model.settings.playerName) { n -> model.updateSettings { it.copy(playerName = n, nameChosen = true) } }
            Spacer(Modifier.height(10.dp))
        }

        // Your code.
        Txt("YOUR FRIEND CODE", size = 13.sp, weight = FontWeight.Bold, color = ui.ink.copy(alpha = 0.65f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Carved(acc.friendCode, 26.sp, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Start)
            WoodButton("Share", { model.platform.share("Play cribbage with me in Maine Cozy Cribbage! My friend code is ${acc.friendCode}") }, size = 13.sp)
        }
        Hand("Give this to a friend so they can add you, or add theirs below.", size = 14.sp)

        Spacer(Modifier.height(12.dp))
        Txt("ADD A FRIEND", size = 13.sp, weight = FontWeight.Bold, color = ui.ink.copy(alpha = 0.65f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                code, { code = it.uppercase().take(16) }, singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, color = ui.ink, fontFamily = ui.fonts.label),
                cursorBrush = SolidColor(ui.ink),
                modifier = Modifier.weight(1f).drawBehind {
                    drawLine(ui.ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f)
                }.padding(vertical = 6.dp),
                decorationBox = { inner -> if (code.isEmpty()) Txt("XXXX-XXXX-XXXX", size = 20.sp, color = ui.ink.copy(alpha = 0.3f)); inner() },
            )
            Spacer(Modifier.width(8.dp))
            WoodButton("Add", { on.addFriend(code) { note = it; code = "" } }, size = 14.sp, primary = code.length >= 12, enabled = code.isNotBlank())
        }
        note?.let { Hand(it, size = 15.sp, color = Color(0xFF2F5B2F)) }

        if (on.incoming.isNotEmpty()) {
            Heading("Friend requests")
            for (r in on.incoming) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Hand(r.name, Modifier.weight(1f), size = 17.sp)
                WoodButton("Accept", { on.respondToRequest(r.id, true) }, size = 13.sp, primary = true)
                Spacer(Modifier.width(6.dp))
                WoodButton("No", { on.respondToRequest(r.id, false) }, size = 13.sp)
            }
        }

        val incomingInvites = on.invites.filter { it.incoming }
        if (incomingInvites.isNotEmpty()) {
            Heading("Invitations")
            for (i in incomingInvites) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Hand("${i.name} wants a game", Modifier.weight(1f), size = 17.sp)
                WoodButton("Play", { on.respondToInvite(i.id, true, onGame = model::openOnlineGame, onError = { note = it }) }, size = 13.sp, primary = true)
                Spacer(Modifier.width(6.dp))
                WoodButton("No", { on.respondToInvite(i.id, false, {}, { note = it }) }, size = 13.sp)
            }
        }

        val active = on.games.filter { !it.over }
        Heading("Your games")
        if (active.isEmpty()) Hand("No games going. Invite a friend below; they can answer whenever they're free.", size = 15.sp)
        for (g in active.sortedByDescending { it.myMove }) GameRow(model, g)
        for (i in on.invites.filter { !it.incoming }) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Hand("Invited ${i.name} · waiting for them", Modifier.weight(1f), size = 15.sp)
            WoodButton("Cancel", { on.cancelInvite(i.id) }, size = 12.sp)
        }

        Heading("Friends")
        if (on.friends.isEmpty()) Hand("No friends yet. Share your code!", size = 15.sp)
        for (f in on.friends) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(10.dp).drawBehind { drawCircle(if (f.online) Color(0xFF4FAF4F) else Color(0xFFA09A90)) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Txt(f.name, size = 17.sp, weight = FontWeight.Medium)
                    Hand("You ${f.wins} – ${f.losses} them" + if (f.skunks > 0) " · ${f.skunks} skunk${if (f.skunks > 1) "s" else ""}" else "", size = 14.sp)
                }
                val playing = active.any { it.playerId == f.playerId }
                val invited = on.invites.any { !it.incoming && it.playerId == f.playerId }
                if (!playing && !invited) WoodButton("Invite", { on.invite(f.playerId, { note = it }) { note = "Invitation sent to ${f.name}." } }, size = 13.sp, primary = true)
                Spacer(Modifier.width(6.dp))
                WoodButton(if (confirmRemove == f.playerId) "Sure?" else "×", { if (confirmRemove == f.playerId) { on.removeFriend(f.playerId); confirmRemove = null } else confirmRemove = f.playerId }, size = 13.sp)
            }
        }
        val finished = on.games.filter { it.over }
        if (finished.isNotEmpty()) {
            Heading("Finished")
            for (g in finished.take(10)) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Hand("${if (g.won == true) "Won" else "Lost"} against ${g.name}, ${g.myScore}–${g.theirScore}" + if (g.skunk > 0) " (skunk)" else "", Modifier.weight(1f), size = 15.sp)
                WoodButton("Clear", { on.hideGame(g.id) }, size = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Hand("Games are saved on the server: play live when you're both here, or take your turn whenever you open the game.", size = 13.sp, color = ui.ink.copy(alpha = 0.7f))
    }
}

@Composable
private fun Heading(t: String) {
    Txt(t.uppercase(), Modifier.padding(top = 16.dp, bottom = 4.dp), size = 13.sp, weight = FontWeight.Bold, color = LocalUi.current.ink.copy(alpha = 0.65f))
}

@Composable
private fun GameRow(model: AppModel, g: GameEntry) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Txt("vs ${g.name}", size = 17.sp, weight = FontWeight.Medium)
            Hand("${g.myScore} to ${g.theirScore} · hand ${g.hand} · " + if (g.myMove) "your turn" else "their turn", size = 14.sp,
                color = if (g.myMove) Color(0xFF2F6B2F) else LocalUi.current.ink)
        }
        WoodButton(if (g.myMove) "Play" else "Open", { model.openOnlineGame(g.id) }, size = 13.sp, primary = g.myMove)
    }
}

