package ru.vinteno.finni.core

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.core.content.Content

/**
 * Норму текста проверяет скрипт, а не глаз — style-guide.md §12.9.
 * Инварианты 10 и 11: фраза не длиннее 5 слов; запрещённых слов и «!» нет; числа не выше 100.
 *
 * Строки, которые нарушают норму дословно в сценарии, не правятся здесь молча:
 * они перечислены в [knownOverLimit] со ссылкой на вопрос в open-questions.md.
 */
class TextLintTest {
    private val texts = Content.fromResources().texts

    /** Дословные тексты сценария сверх нормы — ждут решения, open-questions.md, раздел I. */
    private val knownOverLimit = mapOf<String, String>()

    private val forbidden = listOf(
        "неправильн", "ошибк", "проиграл", "молодец", "не успел", "неверн", "провалил",
        "не справился", "зря", "лучше бы", "надо было", "умница", "отлично", "быстрее",
        "успей", "расстроил", "грустит", "процент", "бюджет", "перерасход",
    )

    private fun words(phrase: String): List<String> =
        phrase.split(Regex("\\s+")).filter { w -> w.any { it.isLetterOrDigit() } || w.startsWith("{") }

    private fun phrases(s: String): List<String> =
        s.split(Regex("(?<=[.?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    @Test fun `фраза не длиннее пяти слов`() {
        val over = texts.strings.filterKeys { it !in knownOverLimit }
            .flatMap { (key, s) -> phrases(s).filter { words(it).size > 5 }.map { "$key: «$it»" } }
        assertTrue("Длиннее 5 слов:\n" + over.joinToString("\n"), over.isEmpty())
    }

    @Test fun `известные нарушения всё ещё нарушения — иначе убрать из списка`() {
        knownOverLimit.keys.forEach { key ->
            assertTrue("$key уже в норме", phrases(texts[key]).any { words(it).size > 5 })
        }
    }

    @Test fun `запрещённых слов и восклицательных знаков нет`() {
        val bad = texts.strings.flatMap { (key, s) ->
            val low = s.lowercase()
            forbidden.filter { low.contains(it) }.map { "$key: «$it»" } +
                (if ('!' in s) listOf("$key: «!»") else emptyList())
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `числа в текстах не выше 100`() {
        val big = texts.strings.flatMap { (key, s) ->
            Regex("\\d+").findAll(s).map { it.value.toInt() }.filter { it > 100 }.map { "$key: $it" }
        }
        assertTrue(big.joinToString("\n"), big.isEmpty())
    }

    @Test fun `формы слова монета`() {
        val forms = listOf(1, 2, 4, 5, 11, 12, 21, 22, 25, 30, 31, 39)
            .associateWith { texts.plural("coin", it) }
        assertTrue(forms.toString(), forms == mapOf(
            1 to "монета", 2 to "монеты", 4 to "монеты", 5 to "монет", 11 to "монет", 12 to "монет",
            21 to "монета", 22 to "монеты", 25 to "монет", 30 to "монет", 31 to "монета", 39 to "монет",
        ))
        assertTrue(texts.format("parcel.amount", "n" to 39) == "Теперь у тебя 39 монет")
    }
}
