package com.geoguy89.cribbage.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.Coach
import com.geoguy89.cribbage.game.Narrator
import com.geoguy89.cribbage.game.Note
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.GameState
import com.geoguy89.cribbage.game.LogEntry
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import com.geoguy89.cribbage.game.ScoreKind
import com.geoguy89.cribbage.net.GameView
import com.geoguy89.cribbage.net.LiveSocket
import com.geoguy89.cribbage.net.OnlineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A floating score: "15 for 2" rising off the table for a moment. */
data class Popup(val id: Int, val player: Int, val text: String, val points: Int)

/**
 * A game at the table, from one player's seat. Solo games run the rules here with the computer opponent; online
 * games mirror the server. The game screen draws either the same way.
 */
abstract class Table {
    abstract val state: GameState
    abstract val me: Int
    abstract val names: List<String>
    abstract val online: Boolean
    /** How many cards each player holds (online, the other hand itself is hidden). */
    abstract val handCounts: List<Int>
    abstract val cribCount: Int
    /** A move is on its way to the server. */
    open val busy: Boolean get() = false
    /** Whether the other player is at the table right now (online). */
    open val opponentHere: Boolean get() = true
    open val title: String get() = if (online) "vs ${names[1 - me]}" else names[1 - me]

    var selected by mutableStateOf<Set<Card>>(emptySet())
    var message by mutableStateOf<String?>(null)
    /** The last hand whose show the player has finished looking at. */
    var shownHand by mutableIntStateOf(0)
    var popups by mutableStateOf<List<Popup>>(emptyList())
    var emote by mutableStateOf<Pair<Int, String>?>(null)
    /** The full tutor's queue of explanations; the game waits while one is showing. */
    open val notes: List<Note> get() = emptyList()
    open fun dismissNote() {}
    val blocked: Boolean get() = notes.isNotEmpty()
    /** The cards on the table when a hand's play ended, kept in view until the show is done. */
    open val endPile: List<com.geoguy89.cribbage.game.Played>? get() = null
    open val endCount: Int get() = 0
    /** The helper's look back at the last card laid (or the player's discard). */
    var review by mutableStateOf<com.geoguy89.cribbage.game.Tip?>(null)
    /** Step by step: the computer is holding its next card until the player taps Continue. */
    open val waiting: Boolean get() = false
    open fun proceed() {}
    /** Peg it yourself: points scored in the play wait for the player to move their own peg. */
    var selfPeg: Boolean = false
        set(value) { field = value; if (!value) pegMoved() }
    /** The score the player's front peg stands on. It trails the real score while a peg is waiting to be moved. */
    var pegAt by mutableIntStateOf(-1)
        private set
    /** Where the player's back peg stands while the front one is waiting to be moved. */
    var pegBack by mutableIntStateOf(-1)
        private set
    /** The score the player's peg should be moved to, if it's waiting. */
    val pegOwed: Int? get() = state.scores[me].takeIf { selfPeg && pegAt >= 0 && it > pegAt && pendingShow == null && !state.over }
    fun pegMoved() {
        if (pegAt == state.scores[me]) return
        pegAt = state.scores[me]
        pegBack = state.prevScores[me]
        afterPeg()
    }
    protected open fun afterPeg() {}
    private var seenSeq = -1
    private var popupId = 0
    /** Sounds and haptics for new events. */
    var onEvent: (LogEntry) -> Unit = {}

    val opponent: Int get() = 1 - me
    val myTurn: Boolean get() = !state.over && when (state.phase) {
        Phase.DISCARD -> state.hands[me].size == 6
        Phase.PEG -> state.turn == me
        Phase.OVER -> false
    }
    /** A show waiting to be walked through. */
    val pendingShow get() = state.lastShow?.takeIf { it.hand > shownHand }

    abstract fun discard(cards: List<Card>)
    abstract fun play(card: Card)
    open fun resign() {}
    open fun sendEmote(i: Int) {}
    open fun close() {}

    open fun ackShow() { state.lastShow?.let { shownHand = maxOf(shownHand, it.hand) }; pegAt = state.scores[me]; pegBack = state.prevScores[me] }

    fun toggle(card: Card) {
        if (busy || blocked || !myTurn) return
        selected = when {
            card in selected -> selected - card
            state.phase == Phase.DISCARD -> (selected + card).let { if (it.size > 2) it.drop(1).toSet() else it }
            else -> setOf(card)
        }
    }

    /** Turns new log entries into popups and sounds. The first look at a state just records where it is. */
    protected fun absorb(s: GameState) {
        if (seenSeq < 0) { seenSeq = s.seq; pegAt = s.scores[me]; pegBack = s.prevScores[me]; return }
        val fresh = s.log.filter { it.seq > seenSeq }
        seenSeq = s.seq
        // The show moves the pegs itself; only points made in the play are pegged by hand.
        if (!selfPeg || s.over || (s.lastShow?.hand ?: 0) > shownHand) pegAt = s.scores[me]
        if (pegAt == s.scores[me]) pegBack = s.prevScores[me]
        for (e in fresh) {
            onEvent(e)
            // Show scores are walked through by the show; only pegging and his heels pop up on the table.
            if (e.count > 0 || e.item.kind == ScoreKind.HEELS || e.item.kind == ScoreKind.SAYS_GO) {
                val text = if (e.item.kind == ScoreKind.SAYS_GO) "Go!" else e.item.label
                popups = (popups + Popup(++popupId, e.player, text, e.item.points)).takeLast(4)
            }
        }
    }

    fun dropPopup(id: Int) { popups = popups.filter { it.id != id } }
}

/**
 * A solo game against the computer. Its moves come after short pauses so they can be followed. With the full tutor
 * on, every step queues a note, and nothing moves on until the player has read it.
 */
class LocalTable(
    private val scope: CoroutineScope,
    val difficulty: Difficulty,
    playerName: String,
    initial: GameState?,
    shown: Int,
    private val seed: Long,
    private val save: (GameState, Int, Long) -> Unit,
    /** Multiplies the computer's thinking time: 1 normal, more is slower. */
    var pace: Float = 1.6f,
    tutor: Boolean = false,
    stepByStep: Boolean = false,
) : Table() {
    private val random = Random(seed)
    override var state by mutableStateOf(initial ?: Rules.newGame(random))
        private set
    override val me = 0
    override val names = listOf(playerName.ifBlank { "You" }, difficulty.title)
    override val online = false
    override val handCounts: List<Int> get() = state.hands.map { it.size }
    override val cribCount: Int get() = state.crib.size
    private var ai: Job? = null
    override var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    override var endPile by mutableStateOf<List<com.geoguy89.cribbage.game.Played>?>(null)
        private set
    override var endCount by mutableIntStateOf(0)
        private set

    var tutor: Boolean = tutor
        set(value) {
            field = value
            if (!value) { notes = emptyList(); drive() }
        }

    /** The computer waits for a tap on Continue before each card it lays, so nothing changes while you're reading. */
    var stepByStep: Boolean = stepByStep
        set(value) { field = value; if (!value && waiting) drive() }
    override var waiting by mutableStateOf(false)
        private set
    /** The player has just said to carry on (Continue, or the tutor's note). */
    private var cleared = false

    override fun proceed() {
        if (!waiting) return
        cleared = true
        drive()
    }

    override fun afterPeg() = drive()

    init {
        shownHand = shown
        absorb(state)
        if (tutor && state.hand == 1 && state.phase == Phase.DISCARD && state.hands[me].size == 6 && pendingShow == null) {
            notes = listOf(Narrator.firstDeal(state, me, names))
        }
        drive()
    }

    private fun narrate(list: List<Note>) { if (tutor && list.isNotEmpty()) notes = notes + list }

    override fun dismissNote() {
        notes = notes.drop(1)
        if (notes.isEmpty()) cleared = true
        drive()
    }

    private fun update(s: GameState) {
        val before = state
        if (s.hand != before.hand) review = null
        state = s
        absorb(s)
        save(s, shownHand, seed)
        if (before.phase == Phase.DISCARD && s.phase == Phase.PEG && s.hand == before.hand) narrate(listOf(Narrator.cut(before, s, me, names)))
        drive()
    }

    override fun ackShow() {
        super.ackShow()
        endPile = null
        save(state, shownHand, seed)
        if (!state.over) narrate(listOf(Narrator.deal(state, me, names)))
        drive()
    }

    override fun discard(cards: List<Card>) {
        if (blocked || state.phase != Phase.DISCARD || state.hands[me].size != 6) return
        selected = emptySet()
        val six = state.hands[me]
        val ownCrib = state.dealer == me
        narrate(listOf(Narrator.discard(six, cards, ownCrib)))
        update(Rules.discard(state, me, cards))
        review = Coach.reviewDiscard(six, cards, ownCrib)
    }

    override fun play(card: Card) {
        if (blocked || !myTurn || state.phase != Phase.PEG || card.value + state.count > 31) return
        if (pegOwed != null) { message = "Move your peg first: tap the glowing hole on the board."; return }
        selected = emptySet()
        laid(me, card)
    }

    /** Lays a card for [player], keeping the last cards of a hand on the table and explaining what happened. */
    private fun laid(player: Int, card: Card) {
        val before = state
        val after = Rules.play(before, player, card, random)
        if (after.hand != before.hand || after.over) {
            endPile = before.pile + com.geoguy89.cribbage.game.Played(player, card)
            endCount = before.count + card.value
        }
        narrate(Narrator.play(before, after, player, card, me, names))
        update(after)
        if (state.hand == before.hand) review = Coach.reviewPlay(before, state, player, card, me, names)
    }

    /** The computer's moves: wait for the player to read the tutor and look at the show, then think and act. */
    private fun drive() {
        ai?.cancel()
        waiting = false
        val s = state
        if (s.over || pendingShow != null || notes.isNotEmpty() || pegOwed != null) return
        val bot = opponent
        val botPlays = s.phase == Phase.PEG && s.turn == bot
        if (botPlays && stepByStep && !cleared) { waiting = true; return }
        val prompt = cleared
        cleared = false
        ai = scope.launch {
            when {
                s.phase == Phase.DISCARD && s.hands[bot].size == 6 -> {
                    delay((700 * pace).toLong())
                    update(Rules.discard(state, bot, Ai.chooseDiscard(state.hands[bot], state.dealer == bot, difficulty, random)))
                }
                s.phase == Phase.PEG && s.turn == bot -> {
                    // After a Continue the player is already waiting on it, so it doesn't dawdle.
                    delay(((if (prompt) 450 else 900 + random.nextInt(500)) * pace).toLong())
                    laid(bot, Ai.choosePlay(state, bot, difficulty, random))
                }
            }
        }
    }

    override fun resign() {
        ai?.cancel()
        notes = emptyList()
        update(Rules.resign(state, me))
    }

    override fun close() { ai?.cancel() }
}

/** A game against a friend, through the server. */
class OnlineTable(
    val gameId: String,
    private val service: OnlineService,
    first: GameView,
    shown: Int,
    private val onShown: (Int) -> Unit,
) : Table() {
    var view by mutableStateOf(first)
        private set
    override val state: GameState get() = view.state
    override val me: Int get() = view.seat
    override val names: List<String> get() = view.players.map { it.name }
    override val online = true
    override val handCounts: List<Int> get() = view.handCounts
    override val cribCount: Int get() = view.cribCount
    override var busy by mutableStateOf(false)
        private set
    override val opponentHere: Boolean get() = view.players.getOrNull(1 - view.seat)?.here == true
    private var socket: LiveSocket? = null
    private var closed = false

    init {
        // Only the latest show is walked through when a game is opened again; older ones are in the log.
        shownHand = maxOf(shown, (first.state.lastShow?.hand ?: 1) - 1)
        absorb(first.state)
        connect()
    }

    private fun accept(v: GameView) {
        if (v.version < view.version) return
        view = v
        absorb(v.state)
    }

    private fun connect() {
        if (closed) return
        socket = service.watch(gameId, onView = ::accept, onEmote = { seat, id ->
            if (seat != view.seat) emote = seat to com.geoguy89.cribbage.net.EMOTES.getOrElse(id) { "" }
        }, onClosed = {
            socket = null
            // Reconnect after a moment, and catch up on anything missed meanwhile.
            if (!closed) service.loadGame(gameId, onError = {}) { accept(it) }
        })
    }

    /** Called every few seconds while the game is open: reopen the socket if it dropped. */
    fun tick() {
        if (socket == null && !closed) connect()
    }

    private fun failed(err: String, v: GameView?) {
        busy = false
        message = err
        v?.let(::accept)
    }

    override fun discard(cards: List<Card>) {
        if (busy) return
        busy = true
        selected = emptySet()
        service.discard(gameId, cards, ::failed) { busy = false; accept(it) }
    }

    override fun play(card: Card) {
        if (busy) return
        if (pegOwed != null) { message = "Move your peg first: tap the glowing hole on the board."; return }
        busy = true
        selected = emptySet()
        service.play(gameId, card, ::failed) { busy = false; accept(it) }
    }

    override fun resign() { service.resign(gameId, ::failed) { accept(it) } }
    override fun sendEmote(i: Int) = service.emote(gameId, i)

    override fun ackShow() {
        super.ackShow()
        onShown(shownHand)
    }

    override fun close() {
        closed = true
        socket?.close()
    }
}
