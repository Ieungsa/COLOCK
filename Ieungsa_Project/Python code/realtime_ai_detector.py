import pyaudio
import numpy as np
import joblib
import time
from collections import deque

class ImprovedAIDetector:
    def __init__(self, model_path='voice_classifier.pkl'):
        """개선된 AI 탐지기"""
        print(f"📦 모델 로드 중: {model_path}")
        self.model = joblib.load(model_path)
        print("✅ 모델 로드 완료!")
        
        self.CHUNK = 1024
        self.RATE = 44100
        self.DURATION = 0.5
        
        # === 개선 파라미터 ===
        self.CONFIDENCE_THRESHOLD = 0.80     # AI 신뢰도 임계값
        self.LOUDNESS_0_100_MIN = 0.0020     # 0-100Hz 최소값
        self.LOUDNESS_100_200_MIN = 0.0035   # 100-200Hz 최소값
        self.CONSECUTIVE_THRESHOLD = 3        # 연속 탐지 횟수
        
        # 연속 탐지 버퍼
        self.prediction_buffer = deque(maxlen=5)
        
        # 통계
        self.total_count = 0
        self.detection_count = 0
        self.false_positive_prevention = 0
    
    def list_devices(self):
        """장치 목록"""
        p = pyaudio.PyAudio()
        print("\n" + "="*70)
        print("사용 가능한 오디오 입력 장치")
        print("="*70)
        
        input_devices = []
        for i in range(p.get_device_count()):
            info = p.get_device_info_by_index(i)
            if info['maxInputChannels'] > 0:
                input_devices.append(i)
                vm = " ⭐ VOICEMOD" if 'voicemod' in info['name'].lower() else ""
                print(f"[{i:2d}] {info['name']}{vm}")
        
        print("="*70)
        p.terminate()
        return input_devices
    
    def extract_features(self, audio_frame):
        """특징 추출"""
        fft_magnitude = np.abs(np.fft.rfft(audio_frame)) / len(audio_frame)
        freqs = np.fft.rfftfreq(len(audio_frame), 1/self.RATE)
        
        features = []
        features.append(np.mean(fft_magnitude[(freqs >= 0) & (freqs <= 100)]))
        features.append(np.mean(fft_magnitude[(freqs >= 100) & (freqs <= 200)]))
        features.append(np.mean(fft_magnitude[(freqs >= 200) & (freqs <= 300)]))
        features.append(np.mean(fft_magnitude[(freqs >= 4000) & (freqs <= 8000)]))
        features.append(np.mean(fft_magnitude[freqs >= 16000]))
        features.append(np.sum(fft_magnitude**2))
        centroid = np.sum(freqs * fft_magnitude) / (np.sum(fft_magnitude) + 1e-10)
        features.append(centroid)
        zcr = np.sum(np.abs(np.diff(np.sign(audio_frame)))) / (2 * len(audio_frame))
        features.append(zcr)
        
        return np.array(features).reshape(1, -1)
    
    def predict_improved(self, audio_frame):
        """개선된 예측 (AI + 규칙 + 스무딩)"""
        
        # 1. 특징 추출
        features = self.extract_features(audio_frame)
        
        # 2. AI 예측
        ai_prediction = self.model.predict(features)[0]
        ai_probability = self.model.predict_proba(features)[0]
        
        # 3. 특징 기반 검증
        loudness_0_100 = features[0, 0]
        loudness_100_200 = features[0, 1]
        
        has_clear_signature = (
            loudness_0_100 >= self.LOUDNESS_0_100_MIN or
            loudness_100_200 >= self.LOUDNESS_100_200_MIN
        )
        
        # 4. AI + 규칙 결합
        if ai_prediction == 1:  # AI가 변조라고 판단
            if ai_probability[1] >= self.CONFIDENCE_THRESHOLD and has_clear_signature:
                # 신뢰도 높고 + 명확한 특징 → 변조
                frame_prediction = 1
            else:
                # 조건 불충분 → 정상으로 수정
                frame_prediction = 0
                self.false_positive_prevention += 1
        else:
            frame_prediction = 0
        
        # 5. 버퍼에 추가
        self.prediction_buffer.append({
            'prediction': frame_prediction,
            'probability': ai_probability[1],
            'loudness_0_100': loudness_0_100,
            'loudness_100_200': loudness_100_200
        })
        
        # 6. 스무딩 (연속 탐지)
        if len(self.prediction_buffer) >= 3:
            modulated_count = sum(p['prediction'] for p in self.prediction_buffer)
            is_modulated = modulated_count >= self.CONSECUTIVE_THRESHOLD
            
            avg_prob = np.mean([p['probability'] for p in self.prediction_buffer])
        else:
            is_modulated = False
            avg_prob = 0.5
        
        return {
            'is_modulated': is_modulated,
            'confidence': avg_prob if is_modulated else 1-avg_prob,
            'ai_prediction': int(ai_prediction),
            'ai_confidence': float(ai_probability[1]),
            'has_signature': has_clear_signature,
            'buffer_count': f"{sum(p['prediction'] for p in self.prediction_buffer)}/{len(self.prediction_buffer)}",
            'features': features[0]
        }
    
    def run_realtime(self, device_index=None):
        """실시간 탐지"""
        p = pyaudio.PyAudio()
        stream = p.open(
            format=pyaudio.paFloat32,
            channels=1,
            rate=self.RATE,
            input=True,
            input_device_index=device_index,
            frames_per_buffer=self.CHUNK
        )
        
        print("\n" + "="*70)
        print("🤖 개선된 AI 음성 변조 탐지 (오탐 감소 버전)")
        print("="*70)
        print(f"💡 설정:")
        print(f"   - AI 신뢰도 임계값: {self.CONFIDENCE_THRESHOLD}")
        print(f"   - 0-100Hz 임계값: {self.LOUDNESS_0_100_MIN}")
        print(f"   - 연속 탐지: {self.CONSECUTIVE_THRESHOLD}/5")
        print("⏹️  종료: Ctrl+C\n")
        
        frames_needed = int((self.RATE / self.CHUNK) * self.DURATION)
        audio_buffer = deque(maxlen=frames_needed * self.CHUNK)
        
        try:
            while True:
                raw_data = stream.read(self.CHUNK, exception_on_overflow=False)
                audio_data = np.frombuffer(raw_data, dtype=np.float32)
                audio_buffer.extend(audio_data)
                
                if len(audio_buffer) >= frames_needed * self.CHUNK:
                    audio_segment = np.array(audio_buffer)
                    volume = np.max(np.abs(audio_segment))
                    
                    if volume > 0.01:
                        result = self.predict_improved(audio_segment)
                        self.total_count += 1
                        
                        if result['is_modulated']:
                            self.detection_count += 1
                            status = "🚨 변조 목소리"
                            color = "\033[91m"
                        else:
                            status = "✅ 정상 목소리"
                            color = "\033[92m"
                        
                        reset = "\033[0m"
                        detection_rate = (self.detection_count / self.total_count * 100) if self.total_count > 0 else 0
                        
                        print(
                            f"\r{color}{status:20s}{reset} | "
                            f"신뢰도: {result['confidence']*100:5.1f}% | "
                            f"AI: {result['ai_confidence']*100:4.1f}% | "
                            f"버퍼: {result['buffer_count']} | "
                            f"0-100Hz: {result['features'][0]:.4f} | "
                            f"특징: {'✓' if result['has_signature'] else '✗'} | "
                            f"오탐방지: {self.false_positive_prevention}회 | "
                            f"탐지율: {detection_rate:4.1f}%  ",
                            end=''
                        )
        
        except KeyboardInterrupt:
            print("\n\n" + "="*70)
            print("📊 최종 통계")
            print("="*70)
            print(f"총 판별: {self.total_count}")
            print(f"변조 탐지: {self.detection_count}")
            print(f"오탐 방지: {self.false_positive_prevention}회")
            print("="*70)
        
        finally:
            stream.stop_stream()
            stream.close()
            p.terminate()

def main():
    print("\n" + "="*70)
    print("🛡️  개선된 음성 변조 탐지 시스템")
    print("="*70)
    
    detector = ImprovedAIDetector()
    
    input_devices = detector.list_devices()
    if not input_devices:
        return
    
    choice = input("\n장치 번호: ").strip()
    device_id = int(choice) if choice else None
    
    detector.run_realtime(device_id)

if __name__ == "__main__":
    main()