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

    override fun onCreateInputView(): View = buildKeyboard()

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
                    "⇧", "⌫", "?123", "ㄱㄴㄷ", "↵"
                )

                val weight = when (label) {
                    "제줏말" -> 4.2f
                    "?123", "ㄱㄴㄷ" -> 1.6f
                    "⇧", "⌫", "↵" -> 1.35f
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
                        setColor(
                            when {
                                label == "," || label == "." ->
                                    if (numberMode) keyColor else specialColor
                                label == "제줏말" -> keyColor
                                special -> specialColor
                                else -> keyColor
                            }
                        )
                        cornerRadius = dp(10).toFloat()
                    }

                    setOnClickListener { handleKey(label) }
                }

                val params = LinearLayout.LayoutParams(
                    0,
                    dp(if (rowIndex == 3) 62 else 58),
                    weight
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
                numberMode = true
                setInputView(buildKeyboard())
            }

            "ㄱㄴㄷ" -> {
                numberMode = false
                setInputView(buildKeyboard())
            }

            "⌫" -> {
                if (!connection.deleteSurroundingText(1, 0)) {
                    connection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    )
                }
            }

            "↵" -> {
                val info = currentInputEditorInfo
                val action = info?.imeOptions?.and(
                    EditorInfo.IME_MASK_ACTION
                ) ?: EditorInfo.IME_ACTION_NONE

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

            "제줏말" -> connection.commitText(" ", 1)
            "☺" -> connection.commitText("☺", 1)
            "⇧" -> { }

            else -> connection.commitText(key, 1)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
