package com.geoguy89.cribbage.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the game server lives (a Cloudflare Worker; see server/). Overridable for tests. */
object OnlineConfig {
    const val DEFAULT_URL = "https://maine-cozy-cribbage.geoguy89.workers.dev"
    var baseUrl: String = DEFAULT_URL
}

// ---- Transport (implemented per platform) ---------------------------------------------------------------------------

interface HttpTransport {
    /** Asynchronous request. [done] gets the status code (-1 when the server can't be reached) and body, on any thread. */
    fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit)
    fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket
}

interface SocketListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onClosed(refused: Boolean)
}

interface LiveSocket {
    fun send(text: String)
    fun close()
}

object NoHttp : HttpTransport {
    override fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit) = done(-1, null)
    override fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket {
        listener.onClosed(refused = true)
        return object : LiveSocket { override fun send(text: String) = Unit; override fun close() = Unit }
    }
}

// ---- Wire types -----------------------------------------------------------------------------------------------------

@Serializable data class Account(val playerId: String, val secret: String, val friendCode: String, val name: String)

@Serializable
data class Friend(
    val playerId: String, val name: String, val online: Boolean = false, val lastSeenAgoMs: Long = 0,
    val wins: Int = 0, val losses: Int = 0, val skunks: Int = 0,
)

@Serializable data class FriendRequest(val id: String, val playerId: String, val name: String)
@Serializable data class Invite(val id: String, val incoming: Boolean, val playerId: String, val name: String, val ageMs: Long = 0)

/** One of your games, as listed on the Friends screen. */
@Serializable
data class GameEntry(
    val id: String, val playerId: String, val name: String, val seat: Int, val over: Boolean,
    val myMove: Boolean, val theirMove: Boolean, val myScore: Int, val theirScore: Int,
    val won: Boolean? = null, val skunk: Int = 0, val hand: Int = 1, val updatedAgoMs: Long = 0,
)

@Serializable data class Me(val playerId: String, val friendCode: String, val name: String, val wins: Int = 0, val losses: Int = 0)

@Serializable
data class SyncResponse(
    val me: Me,
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val games: List<GameEntry> = emptyList(),
)

@Serializable data class Seat(val playerId: String, val name: String, val here: Boolean = false)

/** A game as one player sees it: the other hand, the crib and the deck are empty, with counts in their place. */
@Serializable
data class GameView(
    val gameId: String,
    val version: Int,
    val seat: Int,
    val players: List<Seat>,
    val state: GameState,
    val handCounts: List<Int> = listOf(0, 0),
    val cribCount: Int = 0,
)

/** Table talk: fixed phrases, so there's nothing to moderate. The server knows them by number. */
val EMOTES = listOf(
    "Ayuh!", "Wicked good hand!", "Nice peg!", "Ouch.", "Good luck!", "Well played.",
    "Muggins!", "Hold your horses...", "Down cellar for that one.", "Some lucky!", "Thanks for the game!", "Rematch?",
)

@Serializable private data class NameBody(val name: String)
@Serializable private data class CodeBody(val code: String)
@Serializable private data class PlayerBody(val playerId: String)
@Serializable private data class RespondBody(val id: String, val accept: Boolean)
@Serializable private data class IdBody(val id: String)
@Serializable private data class FriendResult(val status: String, val name: String)
@Serializable private data class ErrorBody(val error: String, val view: GameView? = null)
@Serializable private data class Accepted(val gameId: String? = null)
@Serializable private data class DiscardBody(val type: String = "discard", val cards: List<Int>)
@Serializable private data class PlayBody(val type: String = "play", val card: Int)
@Serializable private data class TypeBody(val type: String)
@Serializable private data class EmoteBody(val type: String = "emote", val id: Int)
@Serializable private data class SocketMsg(val t: String, val view: GameView? = null, val seat: Int = -1, val id: Int = -1)

internal val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/**
 * Friends, invites and games. Callbacks are delivered through [post], which the app drains on the UI thread, so the
 * state here is only touched from that thread.
 */
class OnlineService(
    private val http: HttpTransport,
    private val post: (() -> Unit) -> Unit,
    initialAccount: Account?,
    private val saveAccount: (Account?) -> Unit,
) {
    var account by mutableStateOf(initialAccount)
        private set
    var friends by mutableStateOf<List<Friend>>(emptyList())
        private set
    var incoming by mutableStateOf<List<FriendRequest>>(emptyList())
        private set
    var outgoing by mutableStateOf<List<FriendRequest>>(emptyList())
        private set
    var invites by mutableStateOf<List<Invite>>(emptyList())
        private set
    var games by mutableStateOf<List<GameEntry>>(emptyList())
        private set
    var me by mutableStateOf<Me?>(null)
        private set
    var reachable by mutableStateOf(true)
        private set
    private var registering = false
    private var syncing = false
    var onSynced: (SyncResponse) -> Unit = {}

    private val url get() = OnlineConfig.baseUrl.trimEnd('/')
    private fun auth(): Map<String, String> = account?.let { mapOf("Authorization" to "Bearer ${it.playerId}.${it.secret}") } ?: emptyMap()

    private fun <T> send(method: String, path: String, body: String?, ser: KSerializer<T>?, onError: (String, GameView?) -> Unit = { _, _ -> }, onOk: (T?) -> Unit = {}) {
        http.request(method, url + path, body, auth() + ("Content-Type" to "application/json")) { code, text ->
            post {
                reachable = code != -1
                if (code == 401 && account != null) {
                    // The server doesn't know us (perhaps it was reset): start again with a new account.
                    account = null
                    saveAccount(null)
                }
                when {
                    code in 200..299 -> onOk(ser?.let { s -> text?.let { runCatching { wireJson.decodeFromString(s, it) }.getOrNull() } })
                    code == -1 -> onError("Can't reach the game server. Check your connection.", null)
                    else -> {
                        val err = text?.let { runCatching { wireJson.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
                        onError(err?.error ?: "Server error ($code)", err?.view)
                    }
                }
            }
        }
    }

    private fun <T> enc(ser: KSerializer<T>, v: T) = wireJson.encodeToString(ser, v)

    fun ensureAccount(name: String, then: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (account != null) { then(); return }
        if (registering) return
        registering = true
        send("POST", "/v1/register", enc(NameBody.serializer(), NameBody(name)), Account.serializer(), onError = { e, _ -> registering = false; onError(e) }) { acc ->
            registering = false
            if (acc != null) { account = acc; saveAccount(acc); then() }
        }
    }

    fun sync(done: () -> Unit = {}) {
        if (account == null || syncing) { done(); return }
        syncing = true
        send("GET", "/v1/sync", null, SyncResponse.serializer(), onError = { _, _ -> syncing = false; done() }) { r ->
            syncing = false
            if (r != null) {
                me = r.me
                friends = r.friends; incoming = r.incoming; outgoing = r.outgoing; invites = r.invites; games = r.games
                account?.let { a -> if (a.name != r.me.name) { val u = a.copy(name = r.me.name); account = u; saveAccount(u) } }
                onSynced(r)
            }
            done()
        }
    }

    fun setName(name: String) {
        val a = account ?: return
        account = a.copy(name = name).also(saveAccount)
        send<Unit>("POST", "/v1/profile", enc(NameBody.serializer(), NameBody(name)), null)
    }

    fun addFriend(code: String, done: (String) -> Unit) {
        send("POST", "/v1/friends/request", enc(CodeBody.serializer(), CodeBody(code)), FriendResult.serializer(), onError = { e, _ -> done(e) }) { r ->
            done(if (r?.status == "friends") "You and ${r.name} are friends now!" else "Request sent to ${r?.name ?: "them"}. They'll see it next time they open the game.")
            sync()
        }
    }

    fun respondToRequest(id: String, accept: Boolean) {
        incoming = incoming.filter { it.id != id }
        send<Unit>("POST", "/v1/friends/respond", enc(RespondBody.serializer(), RespondBody(id, accept)), null) { sync() }
    }

    fun removeFriend(playerId: String) {
        friends = friends.filter { it.playerId != playerId }
        send<Unit>("POST", "/v1/friends/remove", enc(PlayerBody.serializer(), PlayerBody(playerId)), null) { sync() }
    }

    fun invite(playerId: String, onError: (String) -> Unit, onOk: () -> Unit) {
        send<Unit>("POST", "/v1/invite", enc(PlayerBody.serializer(), PlayerBody(playerId)), null, onError = { e, _ -> onError(e) }) { onOk(); sync() }
    }

    fun respondToInvite(id: String, accept: Boolean, onGame: (String) -> Unit, onError: (String) -> Unit) {
        invites = invites.filter { it.id != id }
        send("POST", "/v1/invite/respond", enc(RespondBody.serializer(), RespondBody(id, accept)), Accepted.serializer(), onError = { e, _ -> onError(e) }) { r ->
            r?.gameId?.let(onGame)
            sync()
        }
    }

    fun cancelInvite(id: String) {
        invites = invites.filter { it.id != id }
        send<Unit>("POST", "/v1/invite/cancel", enc(IdBody.serializer(), IdBody(id)), null)
    }

    fun hideGame(id: String) {
        games = games.filter { it.id != id }
        send<Unit>("POST", "/v1/game/hide", enc(IdBody.serializer(), IdBody(id)), null)
    }

    fun loadGame(id: String, onError: (String) -> Unit, onView: (GameView) -> Unit) {
        send("GET", "/v1/game/$id", null, GameView.serializer(), onError = { e, _ -> onError(e) }) { v -> if (v != null) onView(v) }
    }

    /** Sends a move. The server answers with the new view, or (for an illegal move) the error and the latest view. */
    private fun act(id: String, body: String, onError: (String, GameView?) -> Unit, onView: (GameView) -> Unit) {
        send("POST", "/v1/game/$id/action", body, GameView.serializer(), onError = onError) { v -> if (v != null) onView(v) }
    }

    fun discard(id: String, cards: List<Card>, onError: (String, GameView?) -> Unit, onView: (GameView) -> Unit) =
        act(id, enc(DiscardBody.serializer(), DiscardBody(cards = cards.map { it.id })), onError, onView)

    fun play(id: String, card: Card, onError: (String, GameView?) -> Unit, onView: (GameView) -> Unit) =
        act(id, enc(PlayBody.serializer(), PlayBody(card = card.id)), onError, onView)

    fun resign(id: String, onError: (String, GameView?) -> Unit, onView: (GameView) -> Unit) =
        act(id, enc(TypeBody.serializer(), TypeBody("resign")), onError, onView)

    fun emote(id: String, emote: Int) {
        send<Unit>("POST", "/v1/game/$id/action", enc(EmoteBody.serializer(), EmoteBody(id = emote)), null)
    }

    /** Live updates for one game: fresh views as either player moves, and the other player's table talk. */
    fun watch(id: String, onView: (GameView) -> Unit, onEmote: (seat: Int, emote: Int) -> Unit, onClosed: () -> Unit): LiveSocket {
        val ws = url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return http.openSocket("$ws/v1/game/$id/ws", auth(), object : SocketListener {
            override fun onOpen() = Unit
            override fun onMessage(text: String) {
                val m = runCatching { wireJson.decodeFromString(SocketMsg.serializer(), text) }.getOrNull() ?: return
                post {
                    when (m.t) {
                        "view" -> m.view?.let(onView)
                        "emote" -> onEmote(m.seat, m.id)
                    }
                }
            }
            override fun onClosed(refused: Boolean) = post(onClosed)
        })
    }
}
