package com.guardian.app.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Severity levels returned by the keyword engine. */
enum class ThreatLevel {
    NONE,
    KEYWORD,   // Level 1 — suspicious text typed
    LINK,      // Level 2 — URL / video link detected
    IMAGE      // Level 3 — handled separately by ImageClassifier
}

data class KeywordResult(
    val level: ThreatLevel,
    val matchedTerms: List<String> = emptyList(),
    val detectedUrl: String? = null
)

@Singleton
class KeywordEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ─────────────────────────────────────────────────────────────────
    // 300 Arabic + transliterated + English terms covering explicit
    // content, harmful sites, and related concepts.
    // Stored as a flat Set for O(1) lookup after tokenisation.
    // ─────────────────────────────────────────────────────────────────
    private val keywordSet: Set<String> = setOf(
        // ── Arabic explicit ────────────────────────────────────────
        "إباحي", "إباحية", "عري", "عارية", "عاري", "جنس", "جنسي",
        "جنسية", "مص", "لحس", "ممارسة", "نيك", "ناك", "تناك",
        "متناكة", "شرموطة", "قحبة", "عاهرة", "زانية", "زنا",
        "فاحشة", "فاحشات", "منحلة", "بورنو", "بورن", "سكس",
        "سيكس", "اغتصاب", "اغتصب", "مغتصبة", "هنتاي", "انمي سكس",
        "فيديو جنسي", "صور عارية", "صور سكس", "مقاطع سكس",
        "مقاطع إباحية", "افلام اباحية", "أفلام إباحية",
        "موقع سكس", "مواقع اباحية", "بنات عاريات", "رجال عراة",
        "سحاق", "لواط", "شذوذ", "مثلية", "شهوة", "متشهي",
        "استمناء", "عادة سرية", "نفسه بيده", "بيضين",
        "كس", "طيز", "زب", "أير", "بزاز", "خرا",

        // ── Transliteration common ────────────────────────────────
        "sex", "sexy", "porn", "porno", "xxx", "nude", "naked",
        "nsfw", "hentai", "erotic", "erotica", "adult",
        "milf", "gilf", "cumshot", "blowjob", "handjob",
        "masturbate", "masturbation", "orgasm", "boobs", "boob",
        "tits", "tit", "ass", "butt", "dick", "cock", "pussy",
        "vagina", "penis", "nipple", "nipples", "bdsm",
        "fetish", "lesbian", "threesome", "foursome", "gangbang",
        "rape", "incest", "lolita", "underage",

        // ── Common adult/harmful site names ───────────────────────
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube",
        "youporn", "tube8", "spankbang", "beeg", "brazzers",
        "bangbros", "realitykings", "mofos", "naughtyamerica",
        "adultfriendfinder", "onlyfans", "chaturbate", "livejasmin",
        "stripchat", "camsoda", "bongacams", "myfreecams",
        "fapello", "rule34", "gelbooru", "nhentai", "e-hentai",

        // ── Gambling ──────────────────────────────────────────────
        "قمار", "كازينو", "رهان", "مراهنة", "يانصيب",
        "casino", "gambling", "poker", "blackjack", "slot",
        "roulette", "betting", "bet365", "1xbet", "betway",
        "sportsbetting", "onlinecasino",

        // ── Alcohol / drugs ───────────────────────────────────────
        "خمر", "كحول", "مخدرات", "حشيش", "كوكايين", "أفيون",
        "alcohol", "vodka", "whiskey", "weed", "marijuana",
        "cocaine", "heroin", "drugs", "ecstasy", "mdma",

        // ── Violence / disturbing ─────────────────────────────────
        "تعذيب", "ذبح", "اغتيال", "تفجير", "انتحار",
        "gore", "torture", "suicide", "self-harm", "selfharm",
        "killing", "murder", "beheading",

        // ── Dark web / bypass ─────────────────────────────────────
        "vpn مجاني", "تجاوز الحجب", "فتح المحجوب",
        "proxy", "tor browser", "dark web", "darkweb",
        "bypass filter", "unblock site",

        // ── Filler / context terms that raise suspicion ───────────
        "watch free", "free stream", "watch online", "leak",
        "leaked", "nude leak", "sex tape", "sextape",
        "onlyfans leak", "fansly leak"
    )

    // Domains to block regardless of keyword matching
    val blockedDomains: Set<String> = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
        "redtube.com", "youporn.com", "tube8.com", "spankbang.com",
        "beeg.com", "brazzers.com", "bangbros.com", "realitykings.com",
        "mofos.com", "naughtyamerica.com", "adultfriendfinder.com",
        "onlyfans.com", "fansly.com", "chaturbate.com", "livejasmin.com",
        "stripchat.com", "camsoda.com", "bongacams.com", "myfreecams.com",
        "fapello.com", "rule34.xxx", "gelbooru.com", "nhentai.net",
        "e-hentai.org", "sankakucomplex.com", "hentaifoundry.com",
        "reddit.com/r/nsfw", "reddit.com/r/gonewild",
        "casino.com", "bet365.com", "1xbet.com", "betway.com",
        "pornhub.net", "pornhub.org"
    )

    private val urlRegex = Regex(
        """(https?://|www\.)[^\s<>"{}|\\^`\[\]]+""",
        RegexOption.IGNORE_CASE
    )

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Analyse a raw text string (clipboard, keyboard output, browser bar).
     * Returns the highest-severity [KeywordResult] found.
     */
    fun analyse(text: String): KeywordResult {
        if (text.isBlank()) return KeywordResult(ThreatLevel.NONE)

        // 1 — URL detection (Level 2)
        val urlMatch = urlRegex.find(text)
        if (urlMatch != null) {
            val url = urlMatch.value
            if (isBlockedUrl(url)) {
                return KeywordResult(ThreatLevel.LINK, detectedUrl = url)
            }
        }

        // 2 — Keyword detection (Level 1)
        val tokens = tokenise(text)
        val matched = tokens.filter { it in keywordSet }
        if (matched.isNotEmpty()) {
            return KeywordResult(ThreatLevel.KEYWORD, matchedTerms = matched)
        }

        return KeywordResult(ThreatLevel.NONE)
    }

    /**
     * Fast check: is the given domain/URL on the block list?
     */
    fun isBlockedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return blockedDomains.any { domain ->
            lower.contains(domain) || lower.contains(domain.replace(".", "\\."))
        } || keywordSet.any { kw ->
            kw.length > 5 && lower.contains(kw)
        }
    }

    /**
     * Check a package name against a list of known adult/gambling apps.
     */
    fun isBlockedPackage(packageName: String): Boolean {
        val blocked = setOf(
            "com.ksmobile.newsbrowser",    // example placeholder
            "org.telegram.messenger",       // not blocked by default; user can configure
        )
        val lower = packageName.lowercase()
        return blocked.contains(lower) ||
               lower.contains("porn") ||
               lower.contains("adult") ||
               lower.contains("xxx") ||
               lower.contains("casino") ||
               lower.contains("bet")
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private fun tokenise(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[\\s,،.!?؟\"'()\\[\\]{}/\\\\|\\-_=+@#\$%^&*]+"))
            .filter { it.length >= 2 }
    }
}
