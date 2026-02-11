package com.ieungsa2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ieungsa2.database.FamilyVerifyDatabase
import com.ieungsa2.database.FamilyVerifyLog
import java.util.Date
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FamilyVerifyActivity : ComponentActivity() {
    private val repository = VerificationRepository()

    override fun attachBaseContext(newBase: android.content.Context) {
        val sharedPref = newBase.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val progress = sharedPref.getInt("font_scale_progress", 0)
        
        val fontScale = when (progress) {
            1 -> 1.2f
            2 -> 1.5f
            else -> 1.0f
        }

        val configuration = android.content.res.Configuration(newBase.resources.configuration)
        configuration.fontScale = fontScale
        
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3B82F6),
                    onPrimary = Color.White,
                    background = Color(0xFFF8FAFC),
                    surface = Color.White,
                    onSurface = Color(0xFF1E293B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UserVerifyScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun UserVerifyScreen() {
        // SharedPreferences에서 저장된 번호 불러오기
        val savedPhone = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("user_phone", "") ?: ""

        var myPhone by remember { mutableStateOf(savedPhone) }
        var targetPhone by remember { mutableStateOf("") }
        var registerStatus by remember { mutableStateOf(if (savedPhone.isNotEmpty()) "✅ 등록됨" else "미등록") }
        var statusMessage by remember { mutableStateOf("1단계: 내 번호를 먼저 등록하세요.") }
        var isWaiting by remember { mutableStateOf(false) }
        var isRegistered by remember { mutableStateOf(savedPhone.isNotEmpty()) }
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color(0xFF1E293B)
                        )
                    }

                    Text(
                        text = "🔐 사칭 방지 서비스",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Color(0xFFEFF6FF),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔐",
                                fontSize = 40.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "사칭 방지 서비스",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "사칭이 의심될 때,\n상대방 앱으로 직접 본인 확인을 요청합니다.",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Input Section Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "전화번호 입력",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 1단계: 내 번호 등록
                        Text(
                            text = "1단계: 내 번호 등록",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = myPhone,
                            onValueChange = { myPhone = it },
                            label = { Text("내 전화번호") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            trailingIcon = {
                                if (isRegistered) {
                                    Text("✅", fontSize = 20.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isRegistered,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                disabledBorderColor = Color(0xFF10B981),
                                disabledTextColor = Color(0xFF1E293B)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (myPhone.isEmpty()) {
                                    Toast.makeText(this@FamilyVerifyActivity, "전화번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // FCM 토큰 가져오기 및 등록
                                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                                    .addOnSuccessListener { token ->
                                        repository.updateMyToken(myPhone, token)

                                        // SharedPreferences에 저장
                                        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                                            .putString("user_phone", myPhone).apply()

                                        isRegistered = true
                                        registerStatus = "✅ 등록됨"
                                        statusMessage = "✅ 내 번호가 등록되었습니다!\n2단계: 상대방 전화번호를 입력하세요."

                                        Toast.makeText(this@FamilyVerifyActivity, "내 번호가 Firebase에 등록되었습니다", Toast.LENGTH_SHORT).show()
                                        android.util.Log.d("FamilyVerify", "내 번호 등록 완료: $myPhone, 토큰: $token")
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this@FamilyVerifyActivity, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = myPhone.isNotEmpty(), // 항상 활성화하여 업데이트 가능하게 함
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRegistered) Color(0xFF10B981) else Color(0xFF3B82F6),
                                disabledContainerColor = Color(0xFFCBD5E1)
                            )
                        ) {
                            Text(
                                text = if (isRegistered) "✅ 정보 업데이트 (재등록)" else "내 번호 등록하기",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 2단계: 상대방 인증
                        Text(
                            text = "2단계: 상대방 인증 요청",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRegistered) Color(0xFF3B82F6) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = targetPhone,
                            onValueChange = { targetPhone = it },
                            label = { Text("상대방 전화번호") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = isRegistered,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                disabledBorderColor = Color(0xFFE2E8F0),
                                disabledTextColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                // Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            statusMessage.contains("성공") -> Color(0xFFD1FAE5)
                            statusMessage.contains("실패") || statusMessage.contains("거절") -> Color(0xFFFEE2E2)
                            isWaiting -> Color(0xFFFEF3C7)
                            else -> Color(0xFFEFF6FF)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isWaiting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF3B82F6),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        Text(
                            text = statusMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                statusMessage.contains("성공") -> Color(0xFF10B981)
                                statusMessage.contains("실패") || statusMessage.contains("거절") -> Color(0xFFEF4444)
                                else -> Color(0xFF64748B)
                            },
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 상대방 인증 요청 버튼
                Button(
                    onClick = {
                        if (targetPhone.isEmpty()) {
                            Toast.makeText(this@FamilyVerifyActivity, "상대방 전화번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isWaiting = true
                        statusMessage = "상대방 주소 확인 중..."

                        repository.getTargetToken(targetPhone) { token ->
                            if (token == null) {
                                isWaiting = false
                                statusMessage = "❌ 실패: $targetPhone\n상대방이 앱을 설치하지 않았거나 번호가 등록되지 않았습니다."
                            } else {
                                statusMessage = "인증 요청을 보내는 중..."
                                repository.sendVerificationRequest(myPhone, targetPhone) { requestId ->
                                    if (requestId != null) {
                                        statusMessage = "✅ 상대방에게 알림이 전송되었습니다!\n상대방의 승인을 기다리는 중..."

                                        // DB에 기록 저장
                                        scope.launch {
                                            val db = FamilyVerifyDatabase.getDatabase(applicationContext)
                                            db.familyVerifyLogDao().insert(
                                                FamilyVerifyLog(
                                                    timestamp = Date(),
                                                    requestId = requestId,
                                                    myPhone = myPhone,
                                                    targetPhone = targetPhone,
                                                    requestType = "SENT",
                                                    status = "PENDING",
                                                    isRead = false
                                                )
                                            )
                                        }

                                        scope.launch {
                                            repository.listenForVerificationResult(requestId).collect { status ->
                                                when (status) {
                                                    "APPROVED" -> {
                                                        isWaiting = false
                                                        statusMessage = "✅ 인증 성공: 실제 본인이 확인되었습니다."

                                                        // DB 상태 업데이트
                                                        scope.launch {
                                                            val db = FamilyVerifyDatabase.getDatabase(applicationContext)
                                                            db.familyVerifyLogDao().updateStatus(requestId, "APPROVED")
                                                        }
                                                    }
                                                    "REJECTED" -> {
                                                        isWaiting = false
                                                        statusMessage = "❌ 인증 거절: 사칭 가능성이 있습니다!"

                                                        // DB 상태 업데이트
                                                        scope.launch {
                                                            val db = FamilyVerifyDatabase.getDatabase(applicationContext)
                                                            db.familyVerifyLogDao().updateStatus(requestId, "REJECTED")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        isWaiting = false
                                        statusMessage = "❌ 요청 전송 실패"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isRegistered && !isWaiting && targetPhone.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        disabledContainerColor = Color(0xFFCBD5E1)
                    )
                ) {
                    Text(
                        text = if (isWaiting) "요청 처리 중..." else "🚨 상대방 인증 요청",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 기록 보기 버튼
                Button(
                    onClick = {
                        val intent = android.content.Intent(this@FamilyVerifyActivity, FamilyVerifyHistoryActivity::class.java)
                        startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF64748B)
                    )
                ) {
                    Text(
                        text = "📋 인증 요청 기록 보기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}