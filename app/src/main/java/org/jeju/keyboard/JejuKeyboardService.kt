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
import android.view.MotionEvent
import android.widget.PopupWindow
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import android.graphics.Paint
import android.content.Context
import android.graphics.Canvas

private class RaisedLabelButton(
    context: Context,
    private val raiseBy: Float
) : Button(context) {

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, -raiseBy)
        super.onDraw(canvas)
        canvas.restore()
    }
}

class JejuKeyboardService : InputMethodService() {

    private var numberMode = false
    private var emojiMode = false
    private var shiftActive = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var lastCommittedArchaicBlock = false
    private var archaicBlockText = ""
    private var activeCharacterPopup: PopupWindow? = null

    private val backgroundColor = Color.rgb(239, 242, 246)
    private val keyColor = Color.rgb(255, 255, 255)
    private val specialColor = Color.rgb(211, 231, 246)

    // Modern Hangul composition state.
    private var lead = -1
    private var vowel = -1
    private var tail = 0
    
    private var archaicLead: String? = null

    private val archaicInitialJamo = mapOf(
        "ㅸ" to "\u112B",
        "ㅿ" to "\u1140",
        "ㅱ" to "\u111D",
        "ㆁ" to "\u114C",
        "ㆆ" to "\u1159",
        "ㆄ" to "\u1157"
    )

    private val leading = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ",
        "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ",
        "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val vowels = listOf(
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
        "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
        "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ",
        "ㆍ", "ᆢ"
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

    private val archaicFinalJamo = mapOf(
        "ㅸ" to "\uD7F0",
        "ㅿ" to "\uD7D0",
        "ㅱ" to "\uD7F3",
        "ㆁ" to "\u11C2",
        "ㆆ" to "\uD7F4",
        "ㆄ" to "\uD7F5"
    )

    
    private val obsoleteConsonants = listOf(
        "ㅸ", "ㅿ", "ㅱ", "ㆁ", "ㆆ", "ㆄ"
    )

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        dismissCharacterPopup()
        super.onStartInputView(info, restarting)
        clearComposition()
        numberMode = false
        emojiMode = false
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        // Nothing to commit if no Korean composition is active.
        if (lead == -1 && archaicLead == null) return

        // We need a valid composing-text range to detect cursor movement.
        if (candidatesStart < 0 || candidatesEnd < 0) return

        // Keep composing while the cursor remains at the end of the syllable.
        val cursorAtCompositionEnd =
            newSelStart == newSelEnd &&
            newSelStart == candidatesEnd

        if (!cursorAtCompositionEnd) {
            // Clear our state first to prevent repeated commits.
            clearComposition()
            currentInputConnection?.finishComposingText()
        }
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
            setBackgroundColor(backgroundColor)
        }

        val rows = when {
            emojiMode -> listOf(
                listOf("😊", "😂", "🥰", "😍", "😭", "🥹", "😅", "🤣"),
                listOf("😉", "😘", "🤗", "😎", "🤔", "😆", "😢", "🥲"),
                listOf("🙈", "🙏", "👍", "🫶", "💪", "👏", "👌", "❤️"),
                listOf("💕", "💗", "💖", "❤️‍🔥", "✨", "🎉", "☀️", "☔"),
                listOf("☕", "🍊", "🍜", "🍻", "📍", "✈️", "🌊", "🌸"),
                listOf("ㄱㄴㄷ", "제줏말", "⌫")
            )
            
            numberMode -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "₩", "~", "_", "-", "+", "(", ")", "/"),
                listOf("☺", "*", "\"", "'", ":", ";", "!", "?", "⌫"),
                listOf("ㄱㄴㄷ", ",", "제줏말", ".", "↵")
            )
            
            else -> listOf(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ",
                    "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ",
                    "ㅗ", "ㅓ", "ㅏ", "ㅣ", "ㆍ"),
                listOf("⇧", "ㅋ", "ㅌ", "ㅊ", "ㅍ",
                    "ㅠ", "ㅜ", "ㅡ", "⌫"),
                listOf("?123", ",", "제줏말", ".", "↵")
            )
        }

        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        
        rows.forEachIndexed { rowIndex, labels ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            labels.forEach { label ->
                val special = label in listOf(
                    "⇧", "⌫", "?123", "ㄱㄴㄷ", "↵", "☺"
                )

                val key = (
                    if (label == "↵") {
                        RaisedLabelButton(this, dp(8).toFloat())
                    } else {
                        Button(this)
                    }
                ).apply {
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    text = when {
                        label == "⇧" && shiftActive -> "⇪"
                        shiftActive -> when (label) {
                            "ㅂ" -> "ㅃ"
                            "ㅈ" -> "ㅉ"
                            "ㄷ" -> "ㄸ"
                            "ㄱ" -> "ㄲ"
                            "ㅅ" -> "ㅆ"
                            "ㅐ" -> "ㅒ"
                            "ㅔ" -> "ㅖ"
                            "ㆍ" -> "ᆢ"
                            else -> label
                        }
                        else -> label
                    }

                    textSize = if (emojiMode) {
                        when (label) {
                            "ㄱㄴㄷ" -> 19f
                            "제줏말" -> 14f
                            "⌫" -> 24f
                            else -> 23f
                        }
                    } else {
                        when (label) {
                            "제줏말" -> 15f
                            "ㄱㄴㄷ" -> 16f
                            "↵" -> 40f
                            "⇧", "⇪", "⌫", "↵" -> 23f
                            "?123" -> 16f
                            else -> 22f
                        }
                    }

                    setTextColor(Color.rgb(31, 37, 43))
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(0, 0, 0, 0)
                    elevation = 0f
                    stateListAnimator = null

                    background = GradientDrawable().apply {
                        val blue = label in listOf(
                            "⇧", "⌫", "?123", "ㄱㄴㄷ",
                            "↵", "☺", ",", "."
                        )
                        setColor(if (blue) specialColor else keyColor)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.rgb(232, 236, 241))
                    }

                    val touchHandler = Handler(Looper.getMainLooper())
                    var popup: PopupWindow? = null
                    var longPressTriggered = false

                    val popupChoices = when (label) {
                        "ㅅ" -> if (shiftActive) listOf("ㅿ") else listOf("ㅆ", "ㅿ")
                        "ㅂ" -> if (shiftActive) listOf("ㅸ") else listOf("ㅃ", "ㅸ")
                        "ㅈ" -> listOf("ㅉ")
                        "ㄷ" -> listOf("ㄸ")
                        "ㄱ" -> listOf("ㄲ")
                        "ㅐ" -> listOf("ㅒ")
                        "ㅔ" -> listOf("ㅖ")
                        "ㆍ" -> listOf("ᆢ")
                        "ㅇ" -> listOf("ㆁ")
                        "ㅎ" -> listOf("ㆆ")
                        "ㅍ" -> listOf("ㆄ")
                        "ㅁ" -> listOf("ㅱ")
                        else -> emptyList()
                    }

                    val openPopup = Runnable {
                        if (popupChoices.isNotEmpty()) {
                            longPressTriggered = true
                            popup = showCharacterPopup(this, popupChoices)
                        }
                    }

                    var backspaceHeld = false
                    var backspaceRepeated = false

                    val backspaceRepeat = object : Runnable {
                        override fun run() {
                            if (!backspaceHeld) return

                            backspaceRepeated = true
                            handleKey("⌫")
                            touchHandler.postDelayed(this, 65L)
                        }
                    }

                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                longPressTriggered = false
                                dismissCharacterPopup()
                                popup = null

                                if (label == "⌫") {
                                    backspaceHeld = true
                                    backspaceRepeated = false
                                    touchHandler.postDelayed(backspaceRepeat, 400L)
                                } else if (popupChoices.isNotEmpty()) {
                                    touchHandler.postDelayed(
                                        openPopup,
                                        android.view.ViewConfiguration
                                            .getLongPressTimeout().toLong()
                                    )
                                }
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val activePopup = popup

                                if (longPressTriggered && activePopup != null) {
                                    val popupContent = activePopup.contentView
                                    val location = IntArray(2)
                                    popupContent.getLocationOnScreen(location)
                                    val popupButtons = popupContent.tag as? List<*>

                                    if (popupButtons != null && popupChoices.isNotEmpty()) {
                                        val index = (
                                            (event.rawX - location[0] - dp(4)) / dp(48)
                                        ).toInt().coerceIn(popupChoices.indices)

                                        popupButtons.forEachIndexed { i, item ->
                                            val button = item as? Button
                                                ?: return@forEachIndexed
                                            button.background = GradientDrawable().apply {
                                                setColor(
                                                    if (i == index) Color.rgb(0, 101, 140)
                                                    else keyColor
                                                )
                                                cornerRadius = dp(8).toFloat()
                                            }
                                        }
                                    }
                                }
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                touchHandler.removeCallbacks(openPopup)
                                backspaceHeld = false
                                touchHandler.removeCallbacks(backspaceRepeat)

                                if (label == "⌫" && !backspaceRepeated) {
                                    handleKey("⌫")
                                }
                                val activePopup = popup

                                if (longPressTriggered && activePopup != null) {
                                    val location = IntArray(2)
                                    activePopup.contentView.getLocationOnScreen(location)

                                    val index = if (popupChoices.size == 1) {
                                        0
                                    } else {
                                        (
                                            (event.rawX - location[0] - dp(4)) / dp(48)
                                        ).toInt().coerceIn(popupChoices.indices)
                                    }

                                    val selected = popupChoices[index]

                                    when {
                                        vowels.contains(selected) -> inputVowel(selected)
                                        archaicInitialJamo.containsKey(selected) -> {
                                            finishComposition()
                                            archaicLead = archaicInitialJamo[selected]
                                            updateComposition()
                                        }
                                        leading.contains(selected) -> inputConsonant(selected)
                                        else -> {
                                            finishComposition()
                                            currentInputConnection?.commitText(selected, 1)
                                        }
                                    }

                                    dismissCharacterPopup()
                                    popup = null

                                    if (!capsLock) {
                                        shiftActive = false
                                        setInputView(buildKeyboard())
                                    }
                                } else if (!longPressTriggered && label != "⌫") {
                                    handleKey(label)
                                }
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                touchHandler.removeCallbacks(openPopup)
                                backspaceHeld = false
                                touchHandler.removeCallbacks(backspaceRepeat)
                                touchHandler.removeCallbacks(openPopup)
                                dismissCharacterPopup()
                                popup = null
                                true
                            }

                            else -> true
                        }
                    }
                }

                if (rowIndex == 0) key.tag = "topRow"

                val weight = when (label) {
                    "⇧", "⇪", "⌫", "☺", "?123", "ㄱㄴㄷ", "↵" -> 1.5f
                    "," , "." -> 0.95f
                    "제줏말" -> 5.3f
                    else -> 1f
                }

                val params = LinearLayout.LayoutParams(
                    0,
                    dp(if (emojiMode) 48 else 54),
                    weight
                )
                params.setMargins(dp(2), dp(2), dp(2), dp(2))
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

    private fun dismissCharacterPopup() {
        activeCharacterPopup?.dismiss()
        activeCharacterPopup = null
    }

    private fun showCharacterPopup(
        anchor: View,
        choices: List<String>
    ): PopupWindow {
        val selectionColor = Color.rgb(0, 101, 140) // #00658C

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(keyColor)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(232, 236, 241))
            }
        }

        val popupButtons = mutableListOf<Button>()

        choices.forEachIndexed { index, character ->
            val button = Button(this).apply {
                text = character
                textSize = 23f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                elevation = 0f
                stateListAnimator = null
                setTextColor(Color.rgb(25, 32, 38))

                background = GradientDrawable().apply {
                    setColor(if (index == 0) selectionColor else keyColor)
                    cornerRadius = dp(8).toFloat()
                }
            }

            popupButtons.add(button)
            content.addView(
                button,
                LinearLayout.LayoutParams(dp(48), dp(52))
            )
        }

        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            elevation = dp(8).toFloat()
        }

        val location = IntArray(2)
        anchor.getLocationInWindow(location)

        val optionWidth = dp(48)
        val popupWidth = optionWidth * choices.size + dp(16)
        val popupHeight = dp(60)

        val x = (
            location[0] + anchor.width / 2 - optionWidth / 2
        ).coerceAtLeast(0)

        val y = if (anchor.tag == "topRow") {
            location[1] - anchor.height - dp(4)
        } else {
            (location[1] - popupHeight - dp(4)).coerceAtLeast(0)
        }

        popup.width = popupWidth
        popup.height = popupHeight

        popup.showAtLocation(
            anchor,
            Gravity.TOP or Gravity.START,
            x,
            y
        )
        
        activeCharacterPopup = popup

        content.tag = popupButtons

        return popup
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return

        when (key) {
            "?123" -> {
                finishComposition()
                emojiMode = false
                numberMode = true
                setInputView(buildKeyboard())
            }

            "ㄱㄴㄷ" -> {
                finishComposition()
                emojiMode = false
                numberMode = false
                setInputView(buildKeyboard())
            }

            "⌫" -> backspace()

            "↵" -> {
                finishComposition()

                val connection = currentInputConnection
                val imeOptions = currentInputEditorInfo?.imeOptions
                    ?: android.view.inputmethod.EditorInfo.IME_ACTION_NONE

                val action = imeOptions and
                    android.view.inputmethod.EditorInfo.IME_MASK_ACTION

                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    connection?.performEditorAction(
                        android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    )
                } else {
                    connection?.commitText("\n", 1)
                }
            }

            
            "제줏말" -> {
                val isArchaic =
                    vowel != -1 &&
                    (vowels[vowel] == "ㆍ" || vowels[vowel] == "ᆢ")

                lastCommittedArchaicBlock = isArchaic

                if (isArchaic && lead != -1) {
                    val initial = when (leading[lead]) {
                        "ㄱ" -> "\u1100"
                        "ㄲ" -> "\u1101"
                        "ㄴ" -> "\u1102"
                        "ㄷ" -> "\u1103"
                        "ㄸ" -> "\u1104"
                        "ㄹ" -> "\u1105"
                        "ㅁ" -> "\u1106"
                        "ㅂ" -> "\u1107"
                        "ㅃ" -> "\u1108"
                        "ㅅ" -> "\u1109"
                        "ㅆ" -> "\u110A"
                        "ㅇ" -> "\u110B"
                        "ㅈ" -> "\u110C"
                        "ㅉ" -> "\u110D"
                        "ㅊ" -> "\u110E"
                        "ㅋ" -> "\u110F"
                        "ㅌ" -> "\u1110"
                        "ㅍ" -> "\u1111"
                        "ㅎ" -> "\u1112"
                        else -> ""
                    }
                    val archaicVowel = if (vowels[vowel] == "ㆍ") {
                        '\u119E'
                    } else {
                        '\u11A2'
                    }

                    val finalJamo = if (tail != 0) {
                        (0x11A7 + tail).toChar()
                    } else {
                        null
                    }

                    archaicBlockText = buildString {
                        append(initial)
                        append(archaicVowel)
                        if (finalJamo != null) append(finalJamo)
                    }
                } else {
                    archaicBlockText = ""
                }

                finishComposition()
                connection.commitText(" ", 1)
            }

            "☺" -> {
                finishComposition()
                numberMode = false
                emojiMode = true
                setInputView(buildKeyboard())
            }

            
            "⇧", "⇪" -> {
                val now = System.currentTimeMillis()

                if (capsLock) {
                    capsLock = false
                    shiftActive = false
                } else if (shiftActive && now - lastShiftTap < 400) {
                    capsLock = true
                    shiftActive = true
                } else {
                    shiftActive = !shiftActive
                }

                lastShiftTap = now
                setInputView(buildKeyboard())
            }


            "," , "." -> {
                finishComposition()
                connection.commitText(key, 1)
            }

            else -> {
                val shifted = if (shiftActive) {
                    when (key) {
                        "ㅂ" -> "ㅃ"
                        "ㅈ" -> "ㅉ"
                        "ㄷ" -> "ㄸ"
                        "ㄱ" -> "ㄲ"
                        "ㅅ" -> "ㅆ"
                        "ㅐ" -> "ㅒ"
                        "ㅔ" -> "ㅖ"
                        "ㆍ" -> "ᆢ"
                        else -> key
                    }
                } else {
                    key
                }

                when {
                    vowels.contains(shifted) -> inputVowel(shifted)
                    archaicInitialJamo.containsKey(shifted) -> {
                        finishComposition()
                        archaicLead = archaicInitialJamo[shifted]
                        updateComposition()
                    }
                    leading.contains(shifted) -> inputConsonant(shifted)
                    else -> {
                        finishComposition()
                        connection.commitText(shifted, 1)
                    }
                }

                if (!capsLock && shiftActive) {
                    shiftActive = false
                    setInputView(buildKeyboard())
                }
            }
        }
    }


    
    private fun inputConsonant(key: String) {
        val connection = currentInputConnection ?: return
        val l = leading.indexOf(key)

        // Handle consonants after an obsolete initial plus vowel.
        if (archaicLead != null) {
            if (vowel == -1) {
                finishComposition()
                inputConsonant(key)
                return
            }

            val t = finals.indexOf(key)

            if (t > 0 && tail == 0) {
                tail = t
                updateComposition()
                return
            }

            if (t > 0 && tail != 0) {
                val combined = finalPairs[finals[tail] + key]

                if (combined != null) {
                    tail = finals.indexOf(combined)
                    updateComposition()
                    return
                }
            }

            finishComposition()

            if (l != -1) {
                lead = l
                updateComposition()
            } else {
                connection.commitText(key, 1)
            }
            return
        }

        if (vowel == -1) {
            if (lead != -1) {
                finishComposition()
            }

            if (l != -1) {
                lead = l
                updateComposition()
            } else {
                connection.commitText(key, 1)
            }
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

                if (l != -1) {
                    lead = l
                    updateComposition()
                } else {
                    connection.commitText(key, 1)
                }
            }
        } else {
            finishComposition()

            if (l != -1) {
                lead = l
                updateComposition()
            } else {
                connection.commitText(key, 1)
            }
        }
    }

    
    private fun inputVowel(key: String) {
        val connection = currentInputConnection ?: return
        val newVowel = vowels.indexOf(key)
        // Handle an obsolete initial consonant.
        if (archaicLead != null) {
            // Complete the obsolete initial with its first vowel.
            if (vowel == -1) {
                vowel = newVowel
                updateComposition()
                return
            }

            // Combine compatible vowels when there is no final consonant.
            val combined = if (tail == 0) {
                vowelPairs[vowels[vowel] + key]
            } else {
                null
            }

            if (combined != null) {
                vowel = vowels.indexOf(combined)
                updateComposition()
                return
            }

            // Move the final consonant to the next syllable.
            if (tail != 0) {
                val oldFinal = finals[tail]

                val cluster = finalPairs.entries.firstOrNull {
                    it.value == oldFinal
                }

                // Keep the first consonant of a compound final behind.
                tail = if (cluster != null) {
                    finals.indexOf(cluster.key.first().toString())
                } else {
                    0
                }

                updateComposition()
                connection.finishComposingText()

                // Move the remaining consonant to the next initial.
                val nextInitial = if (cluster != null) {
                    cluster.key.last().toString()
                } else {
                    oldFinal
                }

                archaicLead = null
                lead = leading.indexOf(nextInitial)
                vowel = newVowel
                tail = 0

                // Use ㅇ if the moved consonant isn't a modern initial.
                if (lead == -1) {
                    lead = leading.indexOf("ㅇ")
                    connection.commitText(nextInitial, 1)
                }

                updateComposition()
                return
            }

            // No final consonant: finish and insert the new vowel separately.
            finishComposition()
            connection.commitText(key, 1)
            return
        }

            // A vowel typed without an initial consonant stays a jamo.
            if (lead == -1 && vowel == -1) {
                connection.commitText(key, 1)
                return
            }

            // Complete a syllable that currently has only its initial consonant.
            if (vowel == -1) {
                vowel = newVowel
                updateComposition()
                return
            }

            // Combine compatible vowels only when there is no final consonant.
            val combined = if (tail == 0) {
                vowelPairs[vowels[vowel] + key]
            } else {
                null
            }

            if (combined != null) {
                vowel = vowels.indexOf(combined)
                updateComposition()
                return
            }

            // A following vowel moves the final consonant to the next syllable.
            if (tail != 0) {
                val oldLead = lead
                val oldVowel = vowel
                val oldTail = tail
                val oldFinal = finals[oldTail]

                val cluster = finalPairs.entries.firstOrNull {
                    it.value == oldFinal
                }

                // Remove the final consonant from the previous syllable.
                tail = if (cluster != null) {
                    finals.indexOf(cluster.key.first().toString())
                } else {
                    0
                }

                updateComposition()
                connection.finishComposingText()

                // A single final becomes the next syllable's initial.
                // For a compound final, move only its second consonant.
                val nextInitial = if (cluster != null) {
                    cluster.key.last().toString()
                } else {
                    oldFinal
                }

                lead = leading.indexOf(nextInitial)
                vowel = newVowel
                tail = 0

                if (lead == -1) {
                    lead = leading.indexOf("ㅇ")
                }

                updateComposition()
                return
            }

            // Do not invent a silent ㅇ when a vowel is typed by itself.
            finishComposition()
            connection.commitText(key, 1)
        }
    
    
    private fun updateComposition() {
        val connection = currentInputConnection ?: return

        // Handle an obsolete initial consonant.
        if (archaicLead != null) {
            val currentArchaicLead = archaicLead!!

            val compatibility = archaicInitialJamo.entries
                .firstOrNull { it.value == currentArchaicLead }
                ?.key ?: currentArchaicLead

            val vowelText = when (vowel) {
                in 0..20 -> vowels.getOrNull(vowel)
                else -> null
            }

            // Before a vowel is typed, display the standalone compatibility character.
            if (vowelText == null) {
                connection.setComposingText(compatibility, 1)
                return
            }

            val jungseong = when (vowelText) {
                "ㆍ" -> "\u119E"
                "ᆢ" -> "\u11A2"
                else -> {
                    val index = vowels.indexOf(vowelText)
                    if (index in 0..20) {
                        (0x1161 + index).toChar().toString()
                    } else {
                        ""
                    }
                }
            }

            val jongseong = if (tail > 0) {
                finals.getOrNull(tail - 1)?.let { final ->
                    val index = finals.indexOf(final)
                    if (index in 0..26) {
                        (0x11A8 + index).toChar().toString()
                    } else {
                        ""
                    }
                } ?: ""
            } else {
                ""
            }

            connection.setComposingText(
                currentArchaicLead + jungseong + jongseong,
                1
            )
            return
        }

        if (lead == -1) return

        // Modern Hangul syllables use the standard Unicode syllable formula.
        // Arae-a and double arae-a must instead be represented as Jamo.
        val vowelText = if (vowel == -1) {
            null
        } else {
            vowels[vowel]
        }

        val text = if (
            vowelText == null ||
            vowelText == "ㆍ" ||
            vowelText == "ᆢ"
        ) {
            if (vowelText == null) {
                leading[lead]
            } else {
                val choseong = listOf(
                    "ㄱ" to "\u1100",
                    "ㄲ" to "\u1101",
                    "ㄴ" to "\u1102",
                    "ㄷ" to "\u1103",
                    "ㄸ" to "\u1104",
                    "ㄹ" to "\u1105",
                    "ㅁ" to "\u1106",
                    "ㅂ" to "\u1107",
                    "ㅃ" to "\u1108",
                    "ㅅ" to "\u1109",
                    "ㅆ" to "\u110A",
                    "ㅇ" to "\u110B",
                    "ㅈ" to "\u110C",
                    "ㅉ" to "\u110D",
                    "ㅊ" to "\u110E",
                    "ㅋ" to "\u110F",
                    "ㅌ" to "\u1110",
                    "ㅍ" to "\u1111",
                    "ㅎ" to "\u1112"
                ).toMap()[leading[lead]] ?: leading[lead]

                val jungseong = when (vowelText) {
                    "ㆍ" -> "\u119E"
                    "ᆢ" -> "\u11A2"
                    else -> ""
                }

                val jongseong = if (tail == 0) {
                    ""
                } else {
                    val trailingJamo = listOf(
                        "", "\u11A8", "\u11A9", "\u11AA", "\u11AB",
                        "\u11AC", "\u11AD", "\u11AE", "\u11AF", "\u11B0",
                        "\u11B1", "\u11B2", "\u11B3", "\u11B4", "\u11B5",
                        "\u11B6", "\u11B7", "\u11B8", "\u11B9", "\u11BA",
                        "\u11BB", "\u11BC", "\u11BD", "\u11BE", "\u11BF",
                        "\u11C0", "\u11C1", "\u11C2"
                    )

                    trailingJamo.getOrElse(tail) { "" }
                }

                choseong + jungseong + jongseong
            }
        } else {
            val modernVowelIndex = listOf(
                "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
                "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
                "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
            ).indexOf(vowelText)

            if (modernVowelIndex == -1) {
                leading[lead]
            } else {
                val codePoint = 0xAC00 +
                    (lead * 21 + modernVowelIndex) * 28 + tail
                String(Character.toChars(codePoint))
            }
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
        archaicLead = null
    }

    
    private fun backspace() {
        val connection = currentInputConnection ?: return
        val selectedText = connection.getSelectedText(0)

        if (!selectedText.isNullOrEmpty()) {
            connection.commitText("", 1)
            clearComposition()
            return
        }

        // Keep normal editing behavior for a syllable still being composed.
        if (tail != 0) {
            tail = 0
            updateComposition()
            return
        }

        if (vowel != -1) {
            vowel = -1
            updateComposition()
            return
        }

        if (lead != -1) {
            clearComposition()
            connection.setComposingText("", 1)
            return
        }

        // Check the actual committed text before the cursor.
        val before = connection.getTextBeforeCursor(32, 0)?.toString() ?: ""

        if (before.isEmpty()) return

        // Unicode Jamo used by archaic blocks.
        val archaicVowels = setOf('\u119E', '\u11A2')
        val leadingJamo = '\u1100'..'\u1112'
        val trailingJamo = '\u11A8'..'\u11C2'

        // Ignore a trailing space for this check; remove it normally first.
        val text = before

        // A block must end with an archaic vowel, optionally followed
        // by one trailing Jamo. The initial Jamo must precede the vowel.
        var end = text.length
        var vowelPosition = end - 1

        if (text[vowelPosition] in trailingJamo) {
            vowelPosition--
        }

        if (
            vowelPosition >= 1 &&
            text[vowelPosition] in archaicVowels &&
            text[vowelPosition - 1] in leadingJamo
        ) {
            val start = vowelPosition - 1
            val blockLength = end - start

            connection.deleteSurroundingText(blockLength, 0)

            lastCommittedArchaicBlock = false
            archaicBlockText = ""
            return
        }

        // Not an archaic block: delete one character as usual.
        connection.deleteSurroundingText(1, 0)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
