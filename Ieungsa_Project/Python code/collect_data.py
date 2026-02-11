import pyaudio
import numpy as np
import json
from datetime import datetime
from collections import deque
import os

class VoiceDataCollector:
    def __init__(self, output_dir='voice_dataset'):
        """
        데이터 수집기 초기화
        
        Args:
            output_dir: 데이터 저장 디렉토리
        """
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
        
        self.CHUNK = 1024
        self.RATE = 44100
        self.DURATION = 0.5
        
        self.samples = []
        self.current_label = None
        
    def list_devices(self):
        """오디오 장치 목록 출력"""
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
        """
        오디오에서 특징 추출
        
        Returns:
            dict: 8개 특징값
        """
        fft_magnitude = np.abs(np.fft.rfft(audio_frame)) / len(audio_frame)
        freqs = np.fft.rfftfreq(len(audio_frame), 1/self.RATE)
        
        features = {}
        
        # 1. 0-100Hz 평균
        mask = (freqs >= 0) & (freqs <= 100)
        features['loudness_0_100'] = float(np.mean(fft_magnitude[mask]))
        
        # 2. 100-200Hz 평균
        mask = (freqs >= 100) & (freqs <= 200)
        features['loudness_100_200'] = float(np.mean(fft_magnitude[mask]))
        
        # 3. 200-300Hz 평균
        mask = (freqs >= 200) & (freqs <= 300)
        features['loudness_200_300'] = float(np.mean(fft_magnitude[mask]))
        
        # 4. 4-8kHz 평균
        mask = (freqs >= 4000) & (freqs <= 8000)
        features['loudness_4k_8k'] = float(np.mean(fft_magnitude[mask]))
        
        # 5. 16kHz+ 평균
        mask = freqs >= 16000
        features['loudness_16k'] = float(np.mean(fft_magnitude[mask]))
        
        # 6. 전체 에너지
        features['total_energy'] = float(np.sum(fft_magnitude**2))
        
        # 7. Spectral Centroid
        centroid = np.sum(freqs * fft_magnitude) / (np.sum(fft_magnitude) + 1e-10)
        features['spectral_centroid'] = float(centroid)
        
        # 8. Zero Crossing Rate
        zcr = np.sum(np.abs(np.diff(np.sign(audio_frame)))) / (2 * len(audio_frame))
        features['zcr'] = float(zcr)
        
        return features
    
    def collect_samples(self, device_index, label, target_count=100):
        """
        샘플 수집
        
        Args:
            device_index: 오디오 장치 번호
            label: 'normal' 또는 'modulated'
            target_count: 수집할 샘플 수
        """
        self.current_label = label
        
        p = pyaudio.PyAudio()
        stream = p.open(
            format=pyaudio.paFloat32,
            channels=1,
            rate=self.RATE,
            input=True,
            input_device_index=device_index,
            frames_per_buffer=self.CHUNK
        )
        
        print(f"\n🎙️  '{label}' 데이터 수집 시작!")
        print(f"목표: {target_count}개 샘플")
        print(f"진행: 계속 말하세요... (Ctrl+C로 중지)\n")
        
        frames_needed = int((self.RATE / self.CHUNK) * self.DURATION)
        audio_buffer = deque(maxlen=frames_needed * self.CHUNK)
        
        collected = 0
        
        try:
            while collected < target_count:
                # 오디오 읽기
                raw_data = stream.read(self.CHUNK, exception_on_overflow=False)
                audio_data = np.frombuffer(raw_data, dtype=np.float32)
                audio_buffer.extend(audio_data)
                
                # 0.5초치 모였으면
                if len(audio_buffer) >= frames_needed * self.CHUNK:
                    audio_segment = np.array(audio_buffer)
                    
                    # 무음 제외 (볼륨 체크)
                    volume = np.max(np.abs(audio_segment))
                    if volume > 0.05:  # 충분히 큰 소리만
                        # 특징 추출
                        features = self.extract_features(audio_segment)
                        
                        # 샘플 저장
                        sample = {
                            'id': f"{label}_{collected:04d}",
                            'label': label,
                            'features': features,
                            'timestamp': datetime.now().isoformat(),
                            'volume': float(volume)
                        }
                        
                        self.samples.append(sample)
                        collected += 1
                        
                        # 진행상황 표시
                        progress = collected / target_count * 100
                        bar_length = 30
                        filled = int(bar_length * collected / target_count)
                        bar = '█' * filled + '░' * (bar_length - filled)
                        
                        print(
                            f"\r[{bar}] {collected}/{target_count} ({progress:.1f}%) | "
                            f"0-100Hz: {features['loudness_0_100']:.4f}  ",
                            end=''
                        )
        
        except KeyboardInterrupt:
            print(f"\n\n⏹️  수집 중단 ({collected}개 수집됨)")
        
        finally:
            stream.stop_stream()
            stream.close()
            p.terminate()
            
            print(f"\n✅ '{label}' 데이터 {collected}개 수집 완료!")
    
    def save_dataset(self, filename=None):
        """데이터셋을 JSON 파일로 저장"""
        if filename is None:
            filename = f"dataset_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        
        filepath = os.path.join(self.output_dir, filename)
        
        dataset = {
            'metadata': {
                'total_samples': len(self.samples),
                'created_at': datetime.now().isoformat(),
                'sample_rate': self.RATE,
                'duration': self.DURATION
            },
            'samples': self.samples
        }
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(dataset, f, indent=2, ensure_ascii=False)
        
        print(f"\n💾 데이터셋 저장 완료: {filepath}")
        print(f"   총 샘플: {len(self.samples)}개")
        
        # 레이블별 통계
        normal_count = sum(1 for s in self.samples if s['label'] == 'normal')
        modulated_count = sum(1 for s in self.samples if s['label'] == 'modulated')
        
        print(f"   정상: {normal_count}개")
        print(f"   변조: {modulated_count}개")
        
        return filepath
    
    def show_statistics(self):
        """수집된 데이터 통계 출력"""
        if not self.samples:
            print("수집된 데이터가 없습니다.")
            return
        
        print("\n" + "="*70)
        print("📊 데이터 통계")
        print("="*70)
        
        # 레이블별 분리
        normal_samples = [s for s in self.samples if s['label'] == 'normal']
        modulated_samples = [s for s in self.samples if s['label'] == 'modulated']
        
        def print_stats(samples, label):
            if not samples:
                return
            
            print(f"\n{label}:")
            
            # 0-100Hz 통계
            loudness_0_100 = [s['features']['loudness_0_100'] for s in samples]
            print(f"  0-100Hz:")
            print(f"    평균: {np.mean(loudness_0_100):.6f}")
            print(f"    최소: {np.min(loudness_0_100):.6f}")
            print(f"    최대: {np.max(loudness_0_100):.6f}")
            print(f"    표준편차: {np.std(loudness_0_100):.6f}")
        
        print_stats(normal_samples, "정상 목소리")
        print_stats(modulated_samples, "변조 목소리")
        
        print("="*70)

# 사용 예시
def main():
    print("\n" + "="*70)
    print("🎙️  음성 변조 탐지 AI - 데이터 수집 시스템")
    print("="*70)
    
    collector = VoiceDataCollector()
    
    # 장치 선택
    input_devices = collector.list_devices()
    if not input_devices:
        print("❌ 입력 장치 없음")
        return
    
    choice = input("\n장치 번호: ").strip()
    device_id = int(choice) if choice else None
    
    # 수집 모드 선택
    print("\n" + "="*70)
    print("수집 모드:")
    print("  1. 정상 목소리 (Voicemod OFF)")
    print("  2. 변조 목소리 (Voicemod ON)")
    print("  3. 둘 다 수집")
    print("="*70)
    
    mode = input("\n모드 선택 (1/2/3): ").strip()
    
    try:
        if mode == '1':
            # 정상 목소리만
            count = int(input("수집할 샘플 수 (권장: 100): ") or "100")
            collector.collect_samples(device_id, 'normal', count)
        
        elif mode == '2':
            # 변조 목소리만
            count = int(input("수집할 샘플 수 (권장: 100): ") or "100")
            collector.collect_samples(device_id, 'modulated', count)
        
        elif mode == '3':
            # 둘 다
            count = int(input("각각 수집할 샘플 수 (권장: 100): ") or "100")
            
            print("\n1단계: 정상 목소리 수집")
            input("준비되면 Enter를 누르세요...")
            collector.collect_samples(device_id, 'normal', count)
            
            print("\n2단계: 변조 목소리 수집")
            print("⚠️  Voicemod를 켜고 변조를 활성화하세요!")
            input("준비되면 Enter를 누르세요...")
            collector.collect_samples(device_id, 'modulated', count)
        
        else:
            print("❌ 잘못된 선택")
            return
        
        # 통계 출력
        collector.show_statistics()
        
        # 저장
        save = input("\n💾 데이터셋을 저장하시겠습니까? (y/n): ").strip().lower()
        if save == 'y':
            collector.save_dataset()
            print("\n✅ 완료! 이제 모델 학습을 진행할 수 있습니다.")
        
    except KeyboardInterrupt:
        print("\n\n중단됨")
    except Exception as e:
        print(f"\n❌ 오류: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()