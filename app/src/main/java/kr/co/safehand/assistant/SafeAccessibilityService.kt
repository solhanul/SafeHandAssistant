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
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showAssistantButton()
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
            background = roundedBackground(Color.rgb(11, 110, 79), 60)
            contentDescription = "손안의 안심비서 빠른 도움"
        }
        panel.addView(actionButton("듣기") { readCurrentScreen() })
        panel.addView(actionButton("검사") { checkLatestUrl() })
        panel.setOnLongClickListener { hideAssistantButton(); true }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        windowManager.addView(panel, params)
        overlay = panel
    }

    private fun actionButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(20, 16, 20, 16)
        setOnClickListener { action() }
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
                    .flatMap { it.lines }
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() && it !in ignoredLabels && !it.matches(Regex("\\d{1,2}:\\d{2}")) }
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

    private fun isEditableSecret(node: AccessibilityNodeInfo) = node.isEditable && (node.inputType and 0x00000080) != 0
    private fun speak(message: String) { tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "safehand") }
    private fun vibrate() { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(650, VibrationEffect.DEFAULT_AMPLITUDE)) }
    private fun hideAssistantButton() { overlay?.let { windowManager.removeView(it) }; overlay = null }
    private fun roundedBackground(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private val statusBarHeight get() = (resources.displayMetrics.density * 32).toInt()
    private val screenHeight get() = resources.displayMetrics.heightPixels

    private companion object {
        const val MAX_READ_LENGTH = 2_500
        val URL_REGEX = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        val ignoredLabels = setOf("듣기", "검사", "뒤로", "닫기", "공유", "더보기", "검색")
    }
}
