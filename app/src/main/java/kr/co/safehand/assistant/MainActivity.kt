package kr.co.safehand.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sos
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

private enum class Screen { ONBOARDING, HOME, LINK_CHECK, FAMILY, READ_ALOUD }

@Composable
private fun SafeHandApp() {
    var screen by remember { mutableStateOf(Screen.ONBOARDING) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Green, background = Cream)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
            when (screen) {
                Screen.ONBOARDING -> Onboarding(onStart = { screen = Screen.HOME })
                Screen.HOME -> Home(
                    onLinkCheck = { screen = Screen.LINK_CHECK },
                    onFamily = { screen = Screen.FAMILY },
                    onRead = { screen = Screen.READ_ALOUD },
                    onEnableAssistant = { context -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Screen.LINK_CHECK -> LinkCheck(onBack = { screen = Screen.HOME })
                Screen.FAMILY -> FamilyHelp(onBack = { screen = Screen.HOME })
                Screen.READ_ALOUD -> ReadAloud(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun Onboarding(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(Icons.Default.Security, null, tint = Green, modifier = Modifier.size(92.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("손안의 안심비서", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Green)
            Spacer(Modifier.height(16.dp))
            Text("스마트폰을 더 쉽고 안전하게\n사용하도록 도와드릴게요.", fontSize = 21.sp, lineHeight = 31.sp, textAlign = TextAlign.Center)
        }
        Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(22.dp)) {
            Text("• 어려운 글자는 소리 내어 읽어드려요\n• 수상한 링크는 먼저 확인해 드려요\n• 필요할 때 가족에게 바로 도움을 요청해요", modifier = Modifier.padding(22.dp), fontSize = 18.sp, lineHeight = 30.sp)
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
            Text("시작하기", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Home(
    onLinkCheck: () -> Unit,
    onFamily: () -> Unit,
    onRead: () -> Unit,
    onEnableAssistant: (Context) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("안녕하세요,", fontSize = 20.sp)
        Text("무엇을 도와드릴까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(22.dp))
        HomeButton("도움 요청", "가족에게 전화 또는 문자 보내기", Icons.Default.Sos, Red, onFamily)
        HomeButton("글자 읽기", "글을 붙여 넣으면 읽어드려요", Icons.Default.Hearing, Green, onRead)
        HomeButton("위험 확인", "수상한 인터넷 주소 검사하기", Icons.Default.Security, Orange, onLinkCheck)
        HomeButton("가족 연락", "등록한 보호자에게 연락하기", Icons.Default.FamilyRestroom, Green, onFamily)
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
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { callNumber(context, "112") },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(10.dp)); Text("긴급 전화 112", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
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
private fun FamilyHelp(onBack: () -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    Scaffold(topBar = { BackBar("가족에게 도움 요청", onBack) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("보호자 연락처", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("가족이나 믿을 수 있는 분의 번호를 입력해 두세요.", fontSize = 17.sp, lineHeight = 25.sp)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("전화번호") }, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp))
            Button(onClick = { callNumber(context, phone) }, enabled = phone.isNotBlank(), modifier = Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(10.dp)); Text("전화로 도움 요청", fontSize = 20.sp) }
            Button(onClick = { sendHelpMessage(context, phone) }, enabled = phone.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Green), modifier = Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(18.dp)) { Text("문자로 도움 요청", fontSize = 20.sp) }
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(18.dp)) { Text("다음 단계에서는 이 번호를 안전하게 저장하고, 홈 화면의 도움 요청 버튼을 길게 눌러 바로 연결되도록 만들 예정입니다.", modifier = Modifier.padding(18.dp), fontSize = 16.sp, lineHeight = 24.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAloud(onBack: () -> Unit) {
    var words by remember { mutableStateOf("") }
    val speaker = rememberSpeaker()
    Scaffold(topBar = { BackBar("글자 읽기", onBack) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(22.dp)) {
            Text("읽고 싶은 글을 붙여 넣어 주세요.", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = words, onValueChange = { words = it }, label = { Text("글 입력") }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), minLines = 6, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 19.sp))
            Button(onClick = { speaker.speak(words, TextToSpeech.QUEUE_FLUSH, null, "safehand-read") }, enabled = words.isNotBlank(), modifier = Modifier.fillMaxWidth().height(62.dp).padding(top = 10.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Hearing, null); Spacer(Modifier.width(10.dp)); Text("소리 내어 읽기", fontSize = 20.sp) }
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
    if (score == 0) reasons += "주소 형식에서 즉시 확인되는 위험 신호는 없어요. 그래도 모르는 곳이라면 열지 마세요."
    return LinkRisk(if (score >= 4) RiskLevel.DANGER else if (score > 0) RiskLevel.CAUTION else RiskLevel.SAFE, reasons)
}

private fun callNumber(context: Context, number: String) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
private fun sendHelpMessage(context: Context, number: String) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", "도움이 필요합니다. 확인 후 연락해 주세요.")) }
private fun vibrate(context: Context) { (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE)) }
