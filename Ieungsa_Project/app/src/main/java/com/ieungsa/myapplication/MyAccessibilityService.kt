package com.ieungsa.myapplication

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VoiceGuard", "Accessibility Service 시작됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow?: return
        val screenText = StringBuilder()

        // 화면의 모든 노드에서 텍스트 수집 [1]
        extractAllText(rootNode, screenText)

        val content = screenText.toString().trim()
        if (content.length > 15) {
            analyzeWithAI(content)
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            extractAllText(node.getChild(i), sb)
        }
    }

    private fun analyzeWithAI(text: String) {
        // 보이스피싱 시나리오 분석을 위한 전용 프롬프트
        val prompt = "아래 스마트폰 화면 내용에 보이스피싱이나 금전 요구 사기 의도가 있나요? 위험하면 'DANGER'를, 안전하면 'SAFE'를 출력하고 짧게 이유를 적어주세요: $text"

        // 간단한 로컬 패턴 매칭으로 위험 감지 (AI 대체)
        val dangerKeywords = listOf(
            "검찰", "경찰", "금융감독원", "국세청", "송금", "계좌이체",
            "피해자", "수사", "압류", "체포", "구속", "보안해제",
            "환급금", "대출", "신용등급", "개인정보", "비밀번호"
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 위험 키워드 감지
                val foundKeywords = dangerKeywords.filter { keyword ->
                    text.contains(keyword, ignoreCase = true)
                }

                if (foundKeywords.isNotEmpty()) {
                    Log.w("MIND_SHIELD", "⚠️ 의심스러운 키워드 발견: ${foundKeywords.joinToString()}")
                    Log.w("MIND_SHIELD", "화면 내용: ${text.take(100)}...")
                } else {
                    Log.d("MIND_SHIELD", "✅ 현재 화면 정상")
                }

                // TODO: API 키 설정 후 실제 AI 분석 활성화
                // val model = generativeModel ?: return@launch
                // val response = model.generateContent(prompt)
                // val resultText = response.text ?: ""
            } catch (e: Exception) {
                Log.e("MIND_SHIELD", "분석 중 오류: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d("MIND_SHIELD", "서비스 중단됨")
    }
}