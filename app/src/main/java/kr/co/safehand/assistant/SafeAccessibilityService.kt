package kr.co.safehand.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.speech.tts.TextToSpeech
import android.graphics.Rect
import android.view.Gravity
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognizer
import java.util.Locale
import kotlin.math.abs

/**
 * 다른 앱에서 사용자가 길게 누른 글자를 읽고, 화면에 표시된 링크를 점검한다.
 * 접근성 정보만 사용하며 입력란·비밀번호는 읽거나 저장하지 않는다.
 */
class SafeAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private val screenTextRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private var latestText = ""
    private var latestVisiblePageText = ""
    private var latestUrl: String? = null
    private var lastWarnedUrl: String? = null
    private var overlay: View? = null
    private var actionPanel: LinearLayout? = null
    private var toggleButton: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private lateinit var windowManager: WindowManager
    private val preferences by lazy { AssistantPreferences(this) }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AssistantPreferences.KEY_FLOATING_BUTTON) {
            if (preferences.floatingButtonEnabled) showAssistantButton() else hideAssistantButton()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences.registerListener(preferenceListener)
        if (preferences.floatingButtonEnabled) showAssistantButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // 상태바와 이 서비스의 오버레이 버튼은 읽기·검사 대상이 아니다.
        val eventPackage = event.packageName?.toString().orEmpty()
        if (eventPackage == packageName || eventPackage == "com.android.systemui") return
        val node = event.source ?: return
        try {
            if (node.isPassword || isEditableSecret(node)) return
            val selected = selectedText(node)
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && selected.isNotBlank()) {
                latestText = selected
                return
            }

            val pageText = visiblePageText()
            if (pageText.isNotBlank()) latestVisiblePageText = pageText
            val url = URL_REGEX.find(pageText)?.value
            if (url != null) {
                latestUrl = url
                warnForRiskyUrl(url)
            }

            // 긴 누른 제목 한 줄이 아니라, 화면에 현재 보이는 본문을 읽는다.
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                latestText = selected.ifBlank { pageText }
                readCurrentScreen()
            }
        } finally {
            node.recycle()
        }
    }

    override fun onInterrupt() { tts?.stop() }

    override fun onDestroy() {
        overlay?.let { windowManager.removeView(it) }
        preferences.unregisterListener(preferenceListener)
        tts?.shutdown()
        screenTextRecognizer.close()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
    }

    private fun showAssistantButton() {
        if (overlay != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            contentDescription = "손안의 안심비서 빠른 도움"
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        actions.addView(actionButton("듣기") { readCurrentScreen() })
        actions.addView(actionButton("검사") { checkLatestUrl() })
        actions.addView(actionButton("멈춤") { stopSpeaking() })
        panel.addView(actions)
        val toggle = actionButton("열기") { toggleActions() }.apply {
            setOnLongClickListener { hideAssistantButton(); true }
        }
        panel.addView(toggle)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val buttonSize = dp(58)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.floatingButtonX.takeIf { it >= 0 } ?: (screenWidth - buttonSize - dp(12))
            y = preferences.floatingButtonY.takeIf { it >= 0 } ?: (screenHeight / 2)
        }
        windowManager.addView(panel, params)
        overlay = panel
        actionPanel = actions
        toggleButton = toggle
        overlayParams = params
        enableDragging(toggle)
    }

    private fun actionButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 0)
        val size = (resources.displayMetrics.density * 58).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply { bottomMargin = (resources.displayMetrics.density * 7).toInt() }
        background = roundedBackground(Color.rgb(11, 110, 79), size / 2)
        setOnClickListener { action() }
    }

    private fun toggleActions() {
        val expanded = actionPanel?.visibility != View.VISIBLE
        actionPanel?.visibility = if (expanded) View.VISIBLE else View.GONE
        toggleButton?.text = if (expanded) "닫기" else "열기"
    }

    private fun collapseActions() {
        actionPanel?.visibility = View.GONE
        toggleButton?.text = "열기"
    }

    private fun enableDragging(button: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val touchSlop = dp(8)
        button.setOnTouchListener { _, event ->
            val params = overlayParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) moved = true
                    if (moved) {
                        val overlayHeight = overlay?.height?.takeIf { it > 0 } ?: dp(58)
                        params.x = (startX + deltaX).toInt().coerceIn(0, resources.displayMetrics.widthPixels - dp(58))
                        params.y = (startY + deltaY).toInt().coerceIn(0, resources.displayMetrics.heightPixels - overlayHeight)
                        overlay?.let { windowManager.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        preferences.floatingButtonX = params.x
                        preferences.floatingButtonY = params.y
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun checkLatestUrl() {
        val url = latestUrl
        if (url == null) {
            speak("이 화면에서 인터넷 주소를 찾지 못했어요. 링크가 보이는 곳을 길게 눌러 보세요.")
            return
        }
        val result = UrlRiskClassifier.check(url)
        speak("${result.summary}. ${result.reason}")
        Toast.makeText(this, "링크 검사: ${result.summary}", Toast.LENGTH_LONG).show()
        if (result.isWarning) vibrate()
    }

    /** 화면의 픽셀을 캡처해 OCR로 읽는다. 웹페이지의 숨은 구조 정보는 사용하지 않는다. */
    private fun readCurrentScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            speak("화면 읽기는 안드로이드 11 이상에서 사용할 수 있어요.")
            return
        }
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) = recognizeVisibleText(screenshot)
            override fun onFailure(errorCode: Int) = speak("화면을 읽지 못했어요. 잠시 후 다시 눌러 주세요.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            readableAppWindow()?.let { takeScreenshotOfWindow(it.id, mainExecutor, callback); return }
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
    }

    private fun recognizeVisibleText(screenshot: AccessibilityService.ScreenshotResult) {
        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            ?.copy(Bitmap.Config.ARGB_8888, false)
        screenshot.hardwareBuffer.close()
        if (bitmap == null) {
            speak("화면 글자를 인식하지 못했어요.")
            return
        }
        screenTextRecognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val visibleText = result.textBlocks
                    // 화면 맨 위 상태 표시줄(시간·통신·배터리)은 본문이 아니다.
                    .filter { block -> (block.boundingBox?.bottom ?: 0) > statusBarHeight }
                    .flatMap { it.lines }
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() && !isIgnoredOcrText(it) }
                    .joinToString(". ")
                    .take(MAX_READ_LENGTH)
                if (visibleText.isBlank()) speak("화면에서 읽을 글자를 찾지 못했어요.") else speak(visibleText)
                bitmap.recycle()
            }
            .addOnFailureListener { bitmap.recycle(); speak("화면 글자를 인식하지 못했어요.") }
    }

    private fun warnForRiskyUrl(url: String) {
        val result = UrlRiskClassifier.check(url)
        if (!result.isWarning || url == lastWarnedUrl) return
        lastWarnedUrl = url
        vibrate()
        speak("주의하세요. 이 화면의 링크가 의심스러울 수 있습니다. 안심비서 검사 버튼을 눌러 확인하세요.")
        Toast.makeText(this, "주의: 의심스러운 링크를 발견했습니다.", Toast.LENGTH_LONG).show()
    }

    private fun visiblePageText(): String {
        val root = readableAppRoot() ?: return ""
        return try { collectVisibleText(root, 0, StringBuilder()).take(MAX_READ_LENGTH) } finally { root.recycle() }
    }

    /**
     * 메뉴·공유창처럼 작은 창이 활성 창으로 바뀌는 경우를 피한다.
     * 화면의 대부분을 차지하는 일반 앱 창만 본문 읽기 대상으로 선택한다.
     */
    private fun readableAppRoot(): AccessibilityNodeInfo? {
        return readableAppWindow()?.root
    }

    private fun readableAppWindow(): AccessibilityWindowInfo? {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        return windows
            .filter { window ->
                val bounds = Rect().also(window::getBoundsInScreen)
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    bounds.width() >= screenWidth * 0.75 && bounds.height() >= screenHeight * 0.70
            }
            .maxByOrNull { window ->
                val bounds = Rect().also(window::getBoundsInScreen)
                bounds.width() * bounds.height()
            }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, depth: Int, values: StringBuilder): String {
        if (depth > 12 || values.length >= MAX_READ_LENGTH || !node.isVisibleToUser || node.isPassword || isEditableSecret(node)) return values.toString()
        val bounds = Rect().also(node::getBoundsInScreen)
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && shouldRead(node, text, bounds)) {
            if (values.isNotEmpty()) values.append(". ")
            values.append(text)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectVisibleText(child, depth + 1, values)
                child.recycle()
            }
        }
        return values.toString()
    }

    private fun selectedText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        return if (start >= 0 && end > start && end <= text.length) text.substring(start, end).trim() else ""
    }

    private fun shouldRead(node: AccessibilityNodeInfo, text: String, bounds: Rect): Boolean {
        val viewClass = node.className?.toString().orEmpty()
        if (bounds.top < statusBarHeight || bounds.bottom > screenHeight || bounds.height() < 12) return false
        if (viewClass.endsWith("Button") || viewClass.endsWith("EditText") || viewClass.endsWith("Switch")) return false
        if (text in ignoredLabels || text.matches(Regex("\\d{1,2}:\\d{2}"))) return false
        return true
    }

    private fun isIgnoredOcrText(text: String): Boolean {
        if (text in ignoredLabels) return true
        val normalized = text.trim()
        return normalized.matches(Regex("\\d{1,2}:\\d{2}")) ||
            normalized.matches(Regex("(오전|오후)\\s*\\d{1,2}:\\d{2}")) ||
            normalized.matches(Regex("\\d{1,2}:\\d{2}\\s*(AM|PM)", RegexOption.IGNORE_CASE))
    }

    private fun isEditableSecret(node: AccessibilityNodeInfo) = node.isEditable && (node.inputType and 0x00000080) != 0
    private fun stopSpeaking() { tts?.stop() }
    private fun speak(message: String) {
        tts?.setSpeechRate(preferences.speechRate)
        val options = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, preferences.speechVolume) }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, options, "safehand")
    }
    private fun vibrate() { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(650, VibrationEffect.DEFAULT_AMPLITUDE)) }
    private fun hideAssistantButton() {
        overlay?.let { windowManager.removeView(it) }
        overlay = null
        actionPanel = null
        toggleButton = null
        overlayParams = null
    }
    private fun roundedBackground(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    private val statusBarHeight get() = (resources.displayMetrics.density * 32).toInt()
    private val screenHeight get() = resources.displayMetrics.heightPixels

    private companion object {
        const val MAX_READ_LENGTH = 2_500
        val URL_REGEX = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        val ignoredLabels = setOf("듣기", "검사", "뒤로", "닫기", "공유", "더보기", "검색")
    }
}
