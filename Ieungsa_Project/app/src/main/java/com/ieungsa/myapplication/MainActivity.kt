package com.ieungsa.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ieungsa.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.all { it.value }
        if (hasPermissions) {
            startVoiceGuardService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServiceRunning = isServiceRunning,
                        hasPermissions = hasPermissions,
                        onStartService = { requestPermissionsAndStart() },
                        onStopService = { stopVoiceGuardService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun getRequiredPermissions(): List<String> {
        val requiredPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return requiredPermissions
    }

    private fun checkPermissions() {
        hasPermissions = getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsAndStart() {
        if (hasPermissions) {
            startVoiceGuardService()
        } else {
            permissionLauncher.launch(getRequiredPermissions().toTypedArray())
        }
    }

    private fun startVoiceGuardService() {
        val intent = Intent(this, CallMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
    }

    private fun stopVoiceGuardService() {
        val intent = Intent(this, CallMonitorService::class.java)
        stopService(intent)
        isServiceRunning = false
    }
}

@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    hasPermissions: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛡️", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("VoiceGuard", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("보이스피싱 방어 시스템", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isServiceRunning) "활성화" else "대기 중",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isServiceRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isServiceRunning) "통화 중 음성 내용을 실시간으로 분석하고 있습니다." else "서비스를 시작하면 통화 중 보호 기능이 활성화됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (isServiceRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { if (isServiceRunning) onStopService() else onStartService() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = hasPermissions,
            colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
        ) {
            Text(
                text = if (isServiceRunning) "보호 중지" else "보호 시작",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!hasPermissions) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "필수 권한을 허용해주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
