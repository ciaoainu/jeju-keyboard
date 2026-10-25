package org.jeju.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

class JejuKeyboardService : InputMethodService() {

    private var numberMode = false

    private val backgroundColor = Color.rgb(225, 239, 250)
    private val keyColor = Color.WHITE
    private val specialColor = Color.rgb(188, 220, 244)

    override fun onCreateInputView(): View {
        return buildKeyboard()
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
            setBackgroundColor(backgroundColor)
        }

        val rows = if (numberMode) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
                listOf("=", "*", "\"", "'", ":", ";", "!", "?", "⌫"),
                listOf("ABC", "☺", "제줏말", "↵")
            )
        } else {
            listOf(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ",
                    "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ",
                    "ㅗ", "ㅓ", "ㅏ", "ㅣ", "ㆍ"),
                listOf("⇧", "ㅋ", "ㅌ", "ㅊ", "ㅍ",
                    "ㅠ", "ㅜ", "ㅡ", "⌫"),
                listOf("?123", "☺", ",", "제줏말", ".", "↵")
            )
        }

        rows.forEachIndexed { rowIndex, labels ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            labels.forEach { label ->
                val special = label in listOf(
                    "⇧", "⌫", "?123", "ABC", "↵", "☺", "제줏말"
                )

                val key = Button(this).apply {
                    text = label
                    textSize = if (label == "제줏말") 13f else 18f
                    setTextColor(Color.rgb(35, 45, 55))
                    typeface = Typeface.DEFAULT
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(0, 0, 0, 0)
                    background = GradientDrawable().apply {
                        setColor(if (special) specialColor else keyColor)
                        cornerRadius = 12f
                    }
                    setOnClickListener { handleKey(label) }
                }

                val weight = when (label) {
                    "제줏말" -> 2.2f
                    "?123", "ABC", "⇧", "⌫", "↵" -> 1.4f
                    else -> 1f
                }

                val params = LinearLayout.LayoutParams(
                    0, dp(if (rowIndex == 3) 48 else 48), weight
                )
                params.setMargins(2, 3, 2, 3)
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
                numberMode = true
                setInputView(buildKeyboard())
            }
            "ABC" -> {
                numberMode = false
                setInputView(buildKeyboard())
            }
            "⌫" -> connection.deleteSurroundingText(1, 0)
            "↵" -> connection.commitText("\n", 1)
            "제줏말" -> connection.commitText(" ", 1)
            "☺" -> connection.commitText("☺", 1)
            "⇧" -> { }
            else -> connection.commitText(key, 1)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
