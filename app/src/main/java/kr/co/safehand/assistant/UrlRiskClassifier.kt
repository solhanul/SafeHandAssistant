package kr.co.safehand.assistant

import java.util.Locale

data class UrlRiskResult(val isWarning: Boolean, val summary: String, val reason: String)

object UrlRiskClassifier {
    fun check(raw: String): UrlRiskResult {
        val url = raw.lowercase(Locale.ROOT)
        var score = 0
        val reasons = mutableListOf<String>()
        if (!url.startsWith("https://")) { score += 2; reasons += "보안 연결이 아니에요" }
        if (listOf("bit.ly", "tinyurl", "t.co", "shorturl").any(url::contains)) { score += 2; reasons += "실제 주소를 숨길 수 있는 짧은 링크예요" }
        if (listOf("verify", "login", "bank", "money", "gift", "인증", "송금", "무료", "당첨").any(url::contains)) { score += 2; reasons += "금융·인증을 유도하는 단어가 있어요" }
        if (url.count { it == '-' } >= 3 || url.length > 100) { score++; reasons += "주소가 복잡해요" }
        return when {
            score >= 4 -> UrlRiskResult(true, "위험할 수 있는 링크예요", reasons.joinToString(", "))
            score > 0 -> UrlRiskResult(true, "주의가 필요한 링크예요", reasons.joinToString(", "))
            else -> UrlRiskResult(false, "즉시 보이는 위험 신호는 없어요", "그래도 모르는 링크는 열지 마세요")
        }
    }
}
