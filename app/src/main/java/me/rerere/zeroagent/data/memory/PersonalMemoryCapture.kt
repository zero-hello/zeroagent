package me.rerere.zeroagent.data.memory

import me.rerere.zeroagent.data.ai.tools.local.SensitiveContentDetector

/** Local capture for clear, durable first-person facts and preferences in an original chat message. */
internal object PersonalMemoryCapture {
    private val preference = Regex(
        """^(?:我(?:平常|通常|一向|習慣|习惯|偏好|比較喜歡|比较喜欢|喜歡|喜欢|的首選是|的首选是)|(?:以後|以后|往後|往后|之後都|之后都)請?|(?:I (?:usually |always )?(?:prefer|like|use)|My preferred|From now on,? please)\s)""",
        RegexOption.IGNORE_CASE,
    )
    enum class Category { PREFERENCE, HABIT, PROFILE, WORKFLOW, IMPORTANT, OTHER }

    private val habit = Regex("""^(?:我(?:平常|通常|一向|習慣|习惯|常用)|I (?:usually|always|use))""", RegexOption.IGNORE_CASE)
    private val profile = Regex(
        """^(?:請叫我|请叫我|我的暱稱是|我的昵称是|我長期從事|我长期从事|我的工作是|Please call me\s|My nickname is\s|I work as\s)""",
        RegexOption.IGNORE_CASE,
    )
    private val workflow = Regex(
        """^(?:我的(?:固定|常用|標準|标准)(?:工作流程|流程|步驟|步骤)|我(?:處理|处理|做|開發|开发|整理).{0,24}(?:一向|通常|一律|固定)先|My (?:usual|standard) workflow\b|I always start by\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val important = Regex(
        """^(?:請(?:一直|長期|长期)?記住[：:，,\s]*|请(?:一直|长期)?记住[：:，,\s]*|(?:Please )?remember (?:that\s|this:\s))\S""",
        RegexOption.IGNORE_CASE,
    )

    fun category(text: String): Category {
        val value = text.trim()
        return when {
            profile.containsMatchIn(value) -> Category.PROFILE
            workflow.containsMatchIn(value) -> Category.WORKFLOW
            important.containsMatchIn(value) -> Category.IMPORTANT
            habit.containsMatchIn(value) -> Category.HABIT
            preference.containsMatchIn(value) -> Category.PREFERENCE
            else -> Category.OTHER
        }
    }

    private val excluded = Regex(
        """https?://|[\p{Cntrl}`<>@/\\?？]|\b(?:password|passcode|credential|secret|api[ _-]?key|token|address|religion|ethnicity|political|medical|passport|credit card)\b|密碼|密码|金鑰|密鑰|密钥|憑證|凭证|住址|地址|身分證|身份证|銀行|银行|驗證碼|验证码|病歷|病历|宗教|政治|性傾向|性取向|\d{5,}""",
        RegexOption.IGNORE_CASE,
    )
    private val ambiguous = Regex(
        """^(?:我(?:喜歡|喜欢|偏好)(?:嗎|吗|不)|["“'「『])|(?:這次|这次|今天|今晚|暫時|暂时|假如|假設|假设|如果)|\b(?:today|tonight|for now|this time|hypothetically|pretend)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(userText: String): List<String> = userText.lineSequence()
        .flatMap { it.split('。', '！', '!', '；', ';').asSequence() }
        .map { it.trim().trimEnd('.').replace(Regex("[ \\t]+"), " ") }
        .filter { it.length in 6..180 && category(it) != Category.OTHER }
        .filterNot { excluded.containsMatchIn(it) || ambiguous.containsMatchIn(it) }
        .filter { SensitiveContentDetector.scan(it).isEmpty() }
        .distinct()
        .take(3)
        .toList()
}
