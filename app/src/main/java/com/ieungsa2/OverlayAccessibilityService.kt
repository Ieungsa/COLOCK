package com.ieungsa2

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class OverlayAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "OverlayAccessibility"
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "접근성 서비스 연결됨 - 메시지 앱 내 알림 기능 비활성화됨")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 메시지 앱에서 알림 표시 기능 완전 비활성화
        // SMS 수신시에만 헤드업 팝업 알림이 표시됩니다
        Log.d(TAG, "접근성 서비스 이벤트 무시됨 - 메시지 앱 내 알림 비활성화")
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "접근성 서비스 중단됨")
    }
}