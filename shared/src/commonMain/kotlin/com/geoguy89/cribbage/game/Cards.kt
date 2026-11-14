package com.geoguy89.cribbage.game

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.random.Random

enum class Suit(val symbol: String, val red: Boolean, val label: String) {
    CLUBS("♣", false, "Clubs"),
    DIAMONDS("♦", true, "Diamonds"),
    HEARTS("♥", true, "Hearts"),
    SPADES("♠", false, "Spades"),
}

/**
 * A playing card. On the wire (and in saves) it is a single number, `suit * 13 + rank - 1`, the same encoding the
 * game server uses: 0 is the ace of clubs, 51 the king of spades.
 */
@Serializable(with = CardSerializer::class)
data class Card(val rank: Int, val suit: Suit) : Comparable<Card> {
    init { require(rank in 1..13) { "rank $rank" } }

    val id: Int get() = suit.ordinal * 13 + rank - 1

    /** Counting value: face cards are ten, aces one. */
    val value: Int get() = if (rank > 10) 10 else rank

    val rankLabel: String get() = when (rank) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString() }
    val rankName: String get() = when (rank) {
        1 -> "Ace"; 11 -> "Jack"; 12 -> "Queen"; 13 -> "King"
        else -> listOf("", "", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")[rank]
    }
    val short: String get() = rankLabel + suit.symbol
    val longName: String get() = "$rankName of ${suit.label}"

    override fun compareTo(other: Card): Int = compareValuesBy(this, other, { it.rank }, { it.suit.ordinal })
    override fun toString() = short

    companion object {
        fun of(id: Int): Card = Card(id % 13 + 1, Suit.entries[id / 13])

        /** "5H", "10S", "JD", "AC" - handy in tests and lessons. */
        fun parse(text: String): Card {
            val t = text.trim().uppercase()
            val suit = when (t.last()) { 'C' -> Suit.CLUBS; 'D' -> Suit.DIAMONDS; 'H' -> Suit.HEARTS; 'S' -> Suit.SPADES; else -> error("suit in $text") }
            val rank = when (val r = t.dropLast(1)) { "A" -> 1; "J" -> 11; "Q" -> 12; "K" -> 13; "T" -> 10; else -> r.toInt() }
            return Card(rank, suit)
        }

        fun list(text: String): List<Card> = text.split(' ', ',').filter { it.isNotBlank() }.map(::parse)

        val deck: List<Card> = (0 until 52).map(::of)

        fun shuffled(random: Random): List<Card> = deck.shuffled(random)
    }
}

object CardSerializer : KSerializer<Card> {
    override val descriptor = PrimitiveSerialDescriptor("Card", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Card) = encoder.encodeInt(value.id)
    override fun deserialize(decoder: Decoder): Card = Card.of(decoder.decodeInt())
}
