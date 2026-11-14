package com.geoguy89.cribbage.theme

import androidx.compose.ui.graphics.Color
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How the table behind everything is drawn. */
enum class Backdrop { PLANKS, PLAID, BIRCH, HARBOR, NIGHT }

/** The look of the room: the table, the signs and the paper. */
enum class BaseTheme(
    val title: String,
    val blurb: String,
    val backdrop: Backdrop,
    /** Wood for the table planks (PLANKS) and the signs and buttons. */
    val tableWood: Wood,
    val signWood: Wood,
    val paper: Color,
    val ink: Color,
    val accent: Color,
    /** Colours behind the backdrop pattern. */
    val bg1: Color,
    val bg2: Color,
    val cardBack: Color,
) {
    PINE_TAVERN("Pine Tavern", "Warm pine planks, a lamp-lit tavern table.", Backdrop.PLANKS, Wood.PINE, Wood.MAPLE,
        Color(0xFFF3E6C8), Color(0xFF3A2414), Color(0xFF5F8F3A), Color(0xFF8A5A2E), Color(0xFF5A3518), Color(0xFF7A2E22)),
    FLANNEL_CABIN("Flannel Cabin", "A red buffalo-check blanket over the camp table.", Backdrop.PLAID, Wood.CHERRY, Wood.MAPLE,
        Color(0xFFF4E8CC), Color(0xFF3A2414), Color(0xFFC0392B), Color(0xFF8E2A20), Color(0xFF2B1712), Color(0xFF1F4E3D)),
    BIRCH_MORNING("Birch Morning", "Bright birch bark and morning sun on the porch.", Backdrop.BIRCH, Wood.BIRCH, Wood.ASH,
        Color(0xFFFFF8E8), Color(0xFF33261A), Color(0xFF4F7D5C), Color(0xFFF2EEE4), Color(0xFFDAD3C4), Color(0xFF2E5A4B)),
    HARBOR_FOG("Harbor Fog", "Slate and sea-smoke, the way the coast wakes up.", Backdrop.HARBOR, Wood.DRIFTWOOD, Wood.DRIFTWOOD,
        Color(0xFFEEEAE0), Color(0xFF26313A), Color(0xFF2E7D8C), Color(0xFF51677A), Color(0xFF26384A), Color(0xFF1F3B57)),
    LOON_NIGHT("Loon Night", "A clear night on the lake, northern lights over the ridge.", Backdrop.NIGHT, Wood.WALNUT, Wood.TEAK,
        Color(0xFFF0E6CF), Color(0xFF2B1D10), Color(0xFF6FD3A8), Color(0xFF0D1B33), Color(0xFF02060F), Color(0xFF16324F)),
}

/** Things that drift across the screen. */
enum class Particles { NONE, SNOW, LEAVES, OAK_LEAVES, HEARTS, CONFETTI, FIREWORKS, PETALS, FIREFLIES, SHAMROCKS, BLUEBERRIES, MAPLE, RAIN, BATS }

/** Decorations along the edges of the table. */
enum class Props { NONE, LIGHTS, WREATH, PUMPKINS, BUNTING, HEARTS, SHAMROCKS, EGGS, BUOYS, BLUEBERRIES, SUGAR_BUCKETS, PINECONES, GOURDS, STARS }

/**
 * The seasons and holidays. Each has its own drifting particles, edge decorations, accent colour and (for some) a
 * change to the music.
 */
enum class Season(
    val title: String,
    val greeting: String,
    val particles: Particles,
    val props: Props,
    val accent: Color?,
    val icon: Sil?,
    val holiday: Boolean,
) {
    WINTER("Winter", "Wicked cold out. Pull up a chair by the stove.", Particles.SNOW, Props.PINECONES, Color(0xFF8FB8D8), null, false),
    MUD_SEASON("Mud Season", "Ice-out on the lakes, mud on the roads.", Particles.RAIN, Props.NONE, Color(0xFF7FA36B), null, false),
    SUMMER("Summer", "Loons calling across the lake tonight.", Particles.FIREFLIES, Props.BUOYS, Color(0xFF3FA7B5), Sil.LOON, false),
    FALL("Foliage", "Peak foliage. The hills are on fire.", Particles.LEAVES, Props.PINECONES, Color(0xFFD9822B), Sil.MAPLE_LEAF, false),
    NEW_YEAR("New Year", "Happy New Year from down east!", Particles.CONFETTI, Props.STARS, Color(0xFFE5C04B), Sil.STAR, true),
    VALENTINES("Valentine's Day", "Fifteen two, and I love you.", Particles.HEARTS, Props.HEARTS, Color(0xFFD94A6A), Sil.HEART, true),
    ST_PATRICKS("Maine Day & St. Patrick's", "Statehood since 1820, and a wee bit o' luck.", Particles.SHAMROCKS, Props.SHAMROCKS, Color(0xFF3E9A55), Sil.SHAMROCK, true),
    MAPLE_SUNDAY("Maine Maple Sunday", "Sugar houses are open. Sap's running!", Particles.MAPLE, Props.SUGAR_BUCKETS, Color(0xFFB8641E), Sil.MAPLE_LEAF, true),
    EASTER("Easter", "Spring blossoms and painted eggs.", Particles.PETALS, Props.EGGS, Color(0xFFB98ACB), Sil.EGG, true),
    INDEPENDENCE("Fourth of July", "Fireworks over the harbor.", Particles.FIREWORKS, Props.BUNTING, Color(0xFFC0392B), Sil.STAR, true),
    BLUEBERRY("Blueberry Season", "Wild blueberries on the barrens.", Particles.BLUEBERRIES, Props.BLUEBERRIES, Color(0xFF4B5BA8), Sil.BLUEBERRY, true),
    HALLOWEEN("Halloween", "Something's peggin' in the dark...", Particles.BATS, Props.PUMPKINS, Color(0xFFE07B24), Sil.PUMPKIN, true),
    THANKSGIVING("Thanksgiving", "Grateful for good friends and a good crib.", Particles.OAK_LEAVES, Props.GOURDS, Color(0xFFB5652B), Sil.OAK_LEAF, true),
    CHRISTMAS("Christmas", "Merry Christmas! Mind the crib.", Particles.SNOW, Props.LIGHTS, Color(0xFFC0392B), Sil.BELL, true);

    companion object {
        /** The season or holiday for [date], as the game picks it automatically. */
        fun forDate(date: LocalDate): Season {
            val m = date.monthNumber
            val d = date.dayOfMonth
            val y = date.year
            fun between(m1: Int, d1: Int, m2: Int, d2: Int): Boolean {
                val v = m * 100 + d
                return v >= m1 * 100 + d1 && v <= m2 * 100 + d2
            }
            if (between(12, 31, 12, 31) || between(1, 1, 1, 2)) return NEW_YEAR
            if (between(2, 7, 2, 14)) return VALENTINES
            if (between(3, 14, 3, 17)) return ST_PATRICKS
            // Maine Maple Sunday is the fourth Sunday in March; the sugar houses open the weekend before it too.
            val maple = nthWeekday(y, 3, DayOfWeek.SUNDAY, 4)
            if (date >= maple.minus(6, DateTimeUnit.DAY) && date <= maple) return MAPLE_SUNDAY
            val easter = easter(y)
            if (date >= easter.minus(7, DateTimeUnit.DAY) && date <= easter.plus(1, DateTimeUnit.DAY)) return EASTER
            if (between(6, 28, 7, 5)) return INDEPENDENCE
            if (between(8, 1, 8, 20)) return BLUEBERRY
            if (between(10, 24, 10, 31)) return HALLOWEEN
            val thanks = nthWeekday(y, 11, DayOfWeek.THURSDAY, 4)
            if (date >= thanks.minus(3, DateTimeUnit.DAY) && date <= thanks.plus(3, DateTimeUnit.DAY)) return THANKSGIVING
            if (between(12, 1, 12, 26)) return CHRISTMAS
            return when (m) {
                12, 1, 2 -> WINTER
                3 -> if (d < 20) WINTER else MUD_SEASON
                4, 5 -> MUD_SEASON
                6, 7, 8 -> SUMMER
                else -> FALL
            }
        }

        fun nthWeekday(year: Int, month: Int, day: DayOfWeek, n: Int): LocalDate {
            var date = LocalDate(year, month, 1)
            while (date.dayOfWeek != day) date = date.plus(1, DateTimeUnit.DAY)
            return date.plus(7 * (n - 1), DateTimeUnit.DAY)
        }

        /** Western Easter (the anonymous Gregorian algorithm). */
        fun easter(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate(year, month, day)
        }
    }
}

/** What's on screen right now: the chosen base look, plus the season's decorations (if any). */
data class Look(val base: BaseTheme, val season: Season?) {
    val accent: Color get() = season?.accent ?: base.accent
}
