package org.jeju.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class JejuKeyboardService : InputMethodService() {

    private var numberMode = false

    private val backgroundColor = Color.rgb(225, 239, 250)
    private val keyColor = Color.WHITE
    private val specialColor = Color.rgb(205, 229, 246)

    // Modern Hangul composition state.
    private var lead = -1
    private var vowel = -1
    private var tail = 0

    private val leading = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ",
        "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ",
        "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val vowels = listOf(
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
        "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
        "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    )

    private val finals = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ",
        "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ",
        "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ",
        "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val vowelPairs = mapOf(
        "ㅗㅏ" to "ㅘ", "ㅗㅐ" to "ㅙ", "ㅗㅣ" to "ㅚ",
        "ㅜㅓ" to "ㅝ", "ㅜㅔ" to "ㅞ", "ㅜㅣ" to "ㅟ",
        "ㅡㅣ" to "ㅢ", "ㅏㅣ" to "ㅐ", "ㅑㅣ" to "ㅒ",
        "ㅓㅣ" to "ㅔ", "ㅕㅣ" to "ㅖ"
    )

    private val finalPairs = mapOf(
        "ㄱㅅ" to "ㄳ", "ㄴㅈ" to "ㄵ", "ㄴㅎ" to "ㄶ",
        "ㄹㄱ" to "ㄺ", "ㄹㅁ" to "ㄻ", "ㄹㅂ" to "ㄼ",
        "ㄹㅅ" to "ㄽ", "ㄹㅌ" to "ㄾ", "ㄹㅍ" to "ㄿ",
        "ㄹㅎ" to "ㅀ", "ㅂㅅ" to "ㅄ"
    )

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearComposition()
        numberMode = false
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 0, 2, 0)
            setBackgroundColor(backgroundColor)
        }

        val rows = if (numberMode) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "₩", "~", "_", "-", "+", "(", ")", "/"),
                listOf("☺", "*", "\"", "'", ":", ";", "!", "?", "⌫"),
                listOf("ㄱㄴㄷ", ",", "제줏말", ".", "↵")
            )
        } else {
            listOf(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ",
                    "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ",
                    "ㅗ", "ㅓ", "ㅏ", "ㅣ", "ㆍ"),
                listOf("⇧", "ㅋ", "ㅌ", "ㅊ", "ㅍ",
                    "ㅠ", "ㅜ", "ㅡ", "⌫"),
                listOf("?123", ",", "제줏말", ".", "↵")
            )
        }

        rows.forEachIndexed { rowIndex, labels ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            labels.forEach { label ->
                val special = label in listOf(
                    "⇧", "⌫", "?123", "ㄱㄴㄷ", "↵", "☺"
                )

                val weight = when (label) {
                    "제줏말" -> 4.2f
                    "?123", "ㄱㄴㄷ" -> 1.6f
                    "⇧", "⌫", "↵", "☺" -> 1.35f
                    else -> 1f
                }

                val key = Button(this).apply {
                    text = label
                    textSize = when (label) {
                        "제줏말" -> 17f
                        "ㄱㄴㄷ" -> 14f
                        else -> 19f
                    }
                    setTextColor(Color.rgb(25, 32, 38))
                    typeface = Typeface.DEFAULT
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(0, 0, 0, 0)

                    background = GradientDrawable().apply {
                        val blue = label in listOf(
                            "⇧", "⌫", "?123", "ㄱㄴㄷ", "↵", "☺", ",", "."
                        )
                        setColor(if (blue) specialColor else keyColor)
                        cornerRadius = dp(10).toFloat()
                    }

                    setOnClickListener { handleKey(label) }
                }

                val params = LinearLayout.LayoutParams(
                    0, dp(if (rowIndex == 3) 62 else 58), weight
                )
                params.setMargins(dp(1), dp(2), dp(1), dp(2))
                row.addView(key, params)
            }

            root.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        return root
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return

        when (key) {
            "?123" -> {
                finishComposition()
                numberMode = true
                setInputView(buildKeyboard())
            }

            "ㄱㄴㄷ" -> {
                finishComposition()
                numberMode = false
                setInputView(buildKeyboard())
            }

            "⌫" -> backspace()

            "↵" -> {
                finishComposition()
                val action = currentInputEditorInfo?.imeOptions
                    ?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE

                if (action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                    action != EditorInfo.IME_ACTION_PREVIOUS
                ) {
                    connection.performEditorAction(action)
                } else {
                    connection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                    )
                    connection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                    )
                }
            }

            "제줏말" -> {
                finishComposition()
                connection.commitText(" ", 1)
            }

            "☺" -> {
                finishComposition()
                connection.commitText("☺", 1)
            }

            "⇧" -> { }

            "," , "." -> {
                finishComposition()
                connection.commitText(key, 1)
            }

            else -> {
                when {
                    vowels.contains(key) -> inputVowel(key)
                    leading.contains(key) -> inputConsonant(key)
                    key == "ㆍ" -> {
                        finishComposition()
                        connection.commitText("ㆍ", 1)
                    }
                    else -> {
                        finishComposition()
                        connection.commitText(key, 1)
                    }
                }
            }
        }
    }

    private fun inputConsonant(key: String) {
        val connection = currentInputConnection ?: return
        val l = leading.indexOf(key)

        if (vowel == -1) {
            if (lead != -1) {
                finishComposition()
            }
            lead = l
            updateComposition()
            return
        }

        val t = finals.indexOf(key)
        if (t > 0 && tail == 0) {
            tail = t
            updateComposition()
        } else if (t > 0 && tail != 0) {
            val combined = finalPairs[finals[tail] + key]
            if (combined != null) {
                tail = finals.indexOf(combined)
                updateComposition()
            } else {
                finishComposition()
                lead = l
                updateComposition()
            }
        } else {
            finishComposition()
            lead = l
            updateComposition()
        }
    }

    private fun inputVowel(key: String) {
        val connection = currentInputConnection ?: return

        if (lead == -1 && vowel == -1) {
            // A vowel on its own uses the silent initial ㅇ.
            lead = leading.indexOf("ㅇ")
            vowel = vowels.indexOf(key)
            updateComposition()
            return
        }

        if (vowel == -1) {
            vowel = vowels.indexOf(key)
            updateComposition()
            return
        }

        val combined = vowelPairs[vowels[vowel] + key]
        if (combined != null && tail == 0) {
            vowel = vowels.indexOf(combined)
            updateComposition()
            return
        }

        if (tail != 0) {
            val oldTail = finals[tail]
            val compound = finals[tail]
            val split = finalPairs.entries.firstOrNull {
                it.value == compound
            }

            finishComposition()

            if (split != null) {
                lead = leading.indexOf(split.key.last().toString())
            } else {
                lead = leading.indexOf(oldTail)
            }

            if (lead == -1) lead = leading.indexOf("ㅇ")
            vowel = vowels.indexOf(key)
            updateComposition()
            return
        }

        finishComposition()
        lead = leading.indexOf("ㅇ")
        vowel = vowels.indexOf(key)
        updateComposition()
    }

    private fun updateComposition() {
        val connection = currentInputConnection ?: return

        if (lead == -1) return

        val text = if (vowel == -1) {
            leading[lead]
        } else {
            val codePoint = 0xAC00 +
                (lead * 21 + vowel) * 28 + tail
            String(Character.toChars(codePoint))
        }

        connection.setComposingText(text, 1)
    }

    private fun finishComposition() {
        currentInputConnection?.finishComposingText()
        clearComposition()
    }

    private fun clearComposition() {
        lead = -1
        vowel = -1
        tail = 0
    }

    private fun backspace() {
        val connection = currentInputConnection ?: return

        if (tail != 0) {
            tail = 0
            updateComposition()
        } else if (vowel != -1) {
            vowel = -1
            updateComposition()
        } else if (lead != -1) {
            clearComposition()
            connection.setComposingText("", 1)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
