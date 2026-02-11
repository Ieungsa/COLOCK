package com.ieungsa.myapplication

import android.telecom.InCallService

/**
 * InCallService를 이용한 통화 오디오 캡처 및 내용 분석
 * - 현재는 CallMonitorService에 집중하기 위해 이 서비스의 모든 기능을 주석 처리함.
 */
class VoiceGuardInCallService : InCallService() {
    /*
    private val TAG = "VoiceGuardInCall"
    private val SPAM_THRESHOLD = 0.8f // 80% 이상일 때 스팸으로 간주

    // 내용 분석 관련
    private lateinit var phishingDetector: PhishingDetector
    private var speechRecognizer: SpeechRecognizer? = null

    // Coroutine
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 상태
    private var isAnalyzing = false
    private var currentCall: Call? = null

    // Call Callback
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            handleCallStateChange(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceGuardInCallService 생성")

        // PhishingDetector 초기화
        phishingDetector = PhishingDetector(this)
        phishingDetector.initialize()

        // SpeechRecognizer 초기화
        setupSpeechRecognizer()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "통화 추가됨: ${call.details?.handle}")
        currentCall = call
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "통화 제거됨")
        call.unregisterCallback(callCallback)
        currentCall = null
        stopLlmDetection()
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> {
                Log.d(TAG, "통화 연결됨! (InCallService)")
                startLlmDetection()
            }
            Call.STATE_DISCONNECTED -> {
                Log.d(TAG, "통화 종료 (InCallService)")
                stopLlmDetection()
            }
        }
    }

    private fun startLlmDetection() {
        if (isAnalyzing) return
        isAnalyzing = true
        Log.d(TAG, "LLM 내용 분석 시작 (InCallService)")
        startListening()
    }

    private fun stopLlmDetection() {
        if (!isAnalyzing) return
        isAnalyzing = false
        Log.d(TAG, "LLM 내용 분석 중지 (InCallService)")
        speechRecognizer?.stopListening()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val transcribedText = matches[0]
                    Log.i(TAG, "최종 인식: $transcribedText")
                    analyzeText(transcribedText)
                }
                if (isAnalyzing) {
                    startListening()
                }
            }

            override fun onError(error: Int) {
                Log.e(TAG, "SpeechRecognizer 오류: $error")
                if (isAnalyzing && shouldRestartListening(error)) {
                    startListening()
                }
            }

            override fun onEndOfSpeech() { }
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "onReadyForSpeech") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun shouldRestartListening(errorCode: Int): Boolean {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> true
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> false
            else -> false
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun analyzeText(text: String) {
        val spamProbability = phishingDetector.detect(text)
        Log.d(TAG, "analyzeText: Spam probability for '$text' is $spamProbability")

        if (spamProbability > SPAM_THRESHOLD) {
            Log.w(TAG, "⚠️ 보이스피싱 의심 문장 감지! (확률: $spamProbability > 임계값: $SPAM_THRESHOLD)")
            // Use the existing AlertManager for notifications
            AlertManager.showWarning(
                context = this@VoiceGuardInCallService,
                confidence = spamProbability,
                details = "피싱 확률: ${(spamProbability * 100).toInt()}%"
            )
        } else {
            Log.d(TAG, "정상 문장으로 판단. (확률: $spamProbability <= 임계값: $SPAM_THRESHOLD)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceGuardInCallService 종료")
        stopLlmDetection()
        phishingDetector.close()
        speechRecognizer?.destroy()
        serviceScope.cancel()
    }
    */
}
