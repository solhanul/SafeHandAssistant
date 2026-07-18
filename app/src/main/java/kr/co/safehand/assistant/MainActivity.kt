package kr.co.safehand.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Cream = Color(0xFFFFFDF8)
private val Green = Color(0xFF0B6E4F)
private val SoftGreen = Color(0xFFE3F4EC)
private val Orange = Color(0xFFF29F05)
private val Red = Color(0xFFB42318)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SafeHandApp() }
    }
}

private enum class Screen { ONBOARDING, HOME, LINK_CHECK, SETTINGS }

private data class TutorialStep(val title: String, val description: String, val icon: ImageVector)

@Composable
private fun SafeHandApp() {
    val context = LocalContext.current
    val preferences = remember { AssistantPreferences(context) }
    var screen by remember { mutableStateOf(if (preferences.onboardingCompleted) Screen.HOME else Screen.ONBOARDING) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Green, background = Cream)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
            when (screen) {
                Screen.ONBOARDING -> OnboardingTutorial(onStart = { preferences.onboardingCompleted = true; screen = Screen.HOME })
                Screen.HOME -> Home(
                    onLinkCheck = { screen = Screen.LINK_CHECK },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onEnableAssistant = { context -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Screen.LINK_CHECK -> LinkCheck(onBack = { screen = Screen.HOME })
                Screen.SETTINGS -> AssistantSettings(onBack = { screen = Screen.HOME }, onReplayTutorial = { screen = Screen.ONBOARDING })
            }
        }
    }
}

@Composable
private fun OnboardingTutorial(onStart: () -> Unit) {
    val steps = remember {
        listOf(
            TutorialStep("손안의 안심비서에 오신 것을 환영해요", "스마트폰을 쉽고 안전하게 사용할 수 있도록 도와드릴게요. 글자는 크게, 버튼은 쉽게 만들었습니다.", Icons.Default.Security),
            TutorialStep("처음 보이는 홈 화면", "홈 화면에는 위험 확인, 안심비서 설정, 화면 위 빠른 도움 켜기 버튼이 있어요. 필요한 버튼을 한 번만 눌러 주세요.", Icons.Default.CheckCircle),
            TutorialStep("화면 위 빠른 도움 버튼", "홈에서 ‘화면 위 빠른 도움 켜기’를 누른 뒤 설정에서 사용을 켜세요. ‘열기’를 누르면 듣기·검사·멈춤이 나타나고, 열기 버튼을 끌어 위치를 옮길 수 있어요.", Icons.Default.Sos),
            TutorialStep("화면의 글자 읽기", "뉴스·메시지·블로그를 보다가 ‘열기’ 다음 ‘듣기’를 누르세요. 지금 화면에 보이는 글자를 읽어드립니다. 글자만 보이면 충분해요.", Icons.Default.Hearing),
            TutorialStep("수상한 링크 확인", "문자나 카카오톡에서 모르는 인터넷 주소가 보이면 누르지 마세요. ‘열기’ 다음 ‘검사’를 누르면 위험 신호를 알려드립니다.", Icons.Default.Security),
            TutorialStep("내게 맞게 설정하기", "안심비서 설정에서 말하는 속도와 음량을 바꾸고, 화면 위 버튼을 숨기거나 이 안내를 다시 볼 수 있어요.", Icons.Default.Tune)
        )
    }
    var stepIndex by remember { mutableStateOf(0) }
    val step = steps[stepIndex]

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("사용 안내 ${stepIndex + 1} / ${steps.size}", fontSize = 17.sp, color = Green)
        Icon(step.icon, null, tint = Green, modifier = Modifier.size(92.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(step.title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.Center, lineHeight = 36.sp)
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(22.dp)) {
                Text(step.description, modifier = Modifier.padding(22.dp), fontSize = 19.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            }
        }
        TutorialVisual(stepIndex)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stepIndex > 0) {
                TextButton(onClick = { stepIndex-- }, modifier = Modifier.weight(1f).height(60.dp)) { Text("이전", fontSize = 19.sp) }
            }
            Button(
                onClick = { if (stepIndex == steps.lastIndex) onStart() else stepIndex++ },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(if (stepIndex == steps.lastIndex) "홈 화면으로 가기" else "다음", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TutorialVisual(stepIndex: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        when (stepIndex) {
            0 -> Text("이 화면은 언제든 설정에서 다시 볼 수 있어요.", modifier = Modifier.padding(18.dp), fontSize = 16.sp, textAlign = TextAlign.Center)
            1 -> Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("이것이 홈 화면이에요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Green)
                TutorialMiniButton("위험 확인", Red)
                TutorialMiniButton("안심비서 설정", Green)
                TutorialMiniButton("화면 위 빠른 도움 켜기", Color(0xFF275D9A))
            }
            2 -> Box(modifier = Modifier.fillMaxWidth().height(225.dp).background(Color(0xFFF4F4F4))) {
                Text("다른 앱을 보는 화면", modifier = Modifier.padding(16.dp), fontSize = 16.sp, color = Color.DarkGray)
                TutorialFabPreview(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
            3 -> Box(modifier = Modifier.fillMaxWidth().height(225.dp).background(Color(0xFFF8F8F8))) {
                Column(modifier = Modifier.padding(14.dp).padding(end = 70.dp)) {
                    Text("오늘의 건강 소식", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text("오늘은 가벼운 산책으로 건강을 챙겨 보세요.", fontSize = 15.sp, lineHeight = 22.sp)
                    Text("화면에 보이는 이 글을 읽어드려요.", fontSize = 15.sp, lineHeight = 22.sp)
                }
                TutorialFabPreview(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp), highlighted = "듣기")
            }
            4 -> Box(modifier = Modifier.fillMaxWidth().height(225.dp).background(Color(0xFFF8F8F8))) {
                Column(modifier = Modifier.padding(14.dp).padding(end = 70.dp)) {
                    Text("새 메시지", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("무료 선물을 받으세요!", fontSize = 15.sp)
                    Text("http://bit.ly/example", fontSize = 15.sp, color = Red)
                    Text("누르기 전에 검사 버튼으로 확인", fontSize = 14.sp, color = Color.DarkGray)
                }
                TutorialFabPreview(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp), highlighted = "검사")
            }
            else -> Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("안심비서 설정", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("말하는 속도     1.0배", fontSize = 15.sp)
                Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FAB 보이기", modifier = Modifier.weight(1f), fontSize = 15.sp)
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }
    }
}

@Composable
private fun TutorialFabPreview(modifier: Modifier = Modifier, highlighted: String? = null) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TutorialFabCircle("듣기", highlighted == "듣기")
        TutorialFabCircle("검사", highlighted == "검사")
        TutorialFabCircle("멈춤", false)
        TutorialFabCircle("닫기", false)
    }
}

@Composable
private fun TutorialFabCircle(label: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .then(if (highlighted) Modifier.border(3.dp, Red, CircleShape) else Modifier)
            .background(Green, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TutorialMiniButton(label: String, color: Color) {
    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(12.dp)).padding(10.dp))
}

@Composable
private fun Home(
    onLinkCheck: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableAssistant: (Context) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("안녕하세요,", fontSize = 20.sp)
        Text("무엇을 도와드릴까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(22.dp))
        HomeButton("위험 확인", "수상한 인터넷 주소 검사하기", Icons.Default.Security, Red, onLinkCheck)
        HomeButton("안심비서 설정", "음성·튜토리얼·화면 위 버튼", Icons.Default.Tune, Green, onOpenSettings)
        Button(
            onClick = { onEnableAssistant(context) },
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(bottom = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF275D9A))
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("화면 위 빠른 도움 켜기", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("설정에서 안심비서를 켜 주세요", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(18.dp)) {
            Text("기억하세요: 모르는 사람이 급하게 돈이나 인증번호를 요구하면, 누르지 말고 가족에게 먼저 물어보세요.", modifier = Modifier.padding(18.dp), fontSize = 17.sp, lineHeight = 26.sp)
        }
    }
}

@Composable
private fun HomeButton(title: String, description: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(94.dp).padding(bottom = 10.dp),
        shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Icon(icon, null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(17.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkCheck(onBack: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LinkRisk?>(null) }
    Scaffold(topBar = { BackBar("위험 확인", onBack) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(22.dp)) {
            Text("인터넷 주소를 붙여 넣어 주세요.", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("검사 결과는 참고용이며, 모르는 주소는 열지 않는 것이 가장 안전합니다.", fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(value = address, onValueChange = { address = it; result = null }, label = { Text("예: https://example.com") }, modifier = Modifier.fillMaxWidth().padding(top = 22.dp), minLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp))
            Button(onClick = { result = assessLink(address) }, modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 10.dp), shape = RoundedCornerShape(18.dp)) { Text("주소 검사하기", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            result?.let { risk ->
                val warning = risk.level != RiskLevel.SAFE
                Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), colors = CardDefaults.cardColors(containerColor = if (warning) Color(0xFFFFE8E6) else SoftGreen), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(risk.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = risk.color)
                        Spacer(Modifier.height(10.dp))
                        risk.reasons.forEach { Text("• $it", fontSize = 17.sp, lineHeight = 27.sp) }
                        if (warning) Button(onClick = { vibrate(context) }, colors = ButtonDefaults.buttonColors(containerColor = risk.color), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("경고 소리·진동으로 다시 알림", fontSize = 17.sp) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSettings(onBack: () -> Unit, onReplayTutorial: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { AssistantPreferences(context) }
    val speaker = rememberSpeaker()
    var speechRate by remember { mutableStateOf(preferences.speechRate) }
    var speechVolume by remember { mutableStateOf(preferences.speechVolume) }
    var floatingButtonEnabled by remember { mutableStateOf(preferences.floatingButtonEnabled) }

    Scaffold(topBar = { BackBar("안심비서 설정", onBack) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("목소리 설정", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("말하는 속도  ${String.format(Locale.KOREAN, "%.1f", speechRate)}배", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Slider(value = speechRate, onValueChange = { speechRate = it; preferences.speechRate = it }, valueRange = 0.5f..1.5f, steps = 9)
                    Text("음량  ${(speechVolume * 100).toInt()}%", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Slider(value = speechVolume, onValueChange = { speechVolume = it; preferences.speechVolume = it }, valueRange = 0.2f..1.0f, steps = 7)
                    Button(onClick = { speakWithSettings(speaker, "안녕하세요. 손안의 안심비서입니다.", preferences) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Hearing, null)
                        Spacer(Modifier.width(8.dp))
                        Text("목소리 미리 듣기", fontSize = 18.sp)
                    }
                }
            }

            Text("화면 위 빠른 도움", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FAB 보이기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("다른 앱에서도 ‘듣기·검사’ 버튼을 표시합니다.", fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    Switch(checked = floatingButtonEnabled, onCheckedChange = {
                        floatingButtonEnabled = it
                        preferences.floatingButtonEnabled = it
                    })
                }
            }

            Button(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.Security, null)
                Spacer(Modifier.width(10.dp))
                Text("튜토리얼 다시 보기", fontSize = 19.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) = TopAppBar(title = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } })

@Composable
private fun rememberSpeaker(): TextToSpeech {
    val context = LocalContext.current
    val speaker = remember { TextToSpeech(context) { } }
    DisposableEffect(Unit) { speaker.language = Locale.KOREAN; onDispose { speaker.shutdown() } }
    return speaker
}

private fun speakWithSettings(speaker: TextToSpeech, words: String, preferences: AssistantPreferences) {
    speaker.setSpeechRate(preferences.speechRate)
    val options = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, preferences.speechVolume) }
    speaker.speak(words, TextToSpeech.QUEUE_FLUSH, options, "safehand-read")
}

private enum class RiskLevel { SAFE, CAUTION, DANGER }
private data class LinkRisk(val level: RiskLevel, val reasons: List<String>) {
    val label get() = when (level) { RiskLevel.SAFE -> "현재는 뚜렷한 위험 신호가 없어요"; RiskLevel.CAUTION -> "주의가 필요한 주소예요"; RiskLevel.DANGER -> "위험할 수 있는 주소예요" }
    val color get() = when (level) { RiskLevel.SAFE -> Green; RiskLevel.CAUTION -> Orange; RiskLevel.DANGER -> Red }
}

private fun assessLink(raw: String): LinkRisk {
    if (raw.isBlank()) return LinkRisk(RiskLevel.CAUTION, listOf("주소를 입력해 주세요."))
    val text = raw.lowercase(Locale.getDefault())
    val reasons = mutableListOf<String>()
    var score = 0
    if (!text.startsWith("https://")) { score += 2; reasons += "보안 연결(https)을 사용하지 않아요." }
    if (listOf("bit.ly", "tinyurl", "t.co", "shorturl").any(text::contains)) { score += 2; reasons += "실제 주소를 숨길 수 있는 짧은 링크예요." }
    if (listOf("verify", "login", "bank", "money", "gift", "인증", "송금", "무료", "당첨").any(text::contains)) { score += 2; reasons += "금융·인증 또는 혜택을 유도하는 단어가 있어요." }
    if (text.count { it == '-' } >= 3 || text.length > 100) { score += 1; reasons += "주소가 지나치게 복잡하거나 길어요." }
    if (score == 0) reasons += "주소 형식에서 즉시 확인되는 위험 신호는 없어요."
    return LinkRisk(if (score >= 4) RiskLevel.DANGER else if (score > 0) RiskLevel.CAUTION else RiskLevel.SAFE, reasons)
}

private fun vibrate(context: Context) { (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE)) }
