import pyaudio
import numpy as np
import matplotlib.pyplot as plt
from collections import deque
import platform

# 한글 폰트 설정
import matplotlib.font_manager as fm

def setup_korean_font():
    """한글 폰트 설정"""
    system = platform.system()
    
    if system == 'Windows':
        plt.rcParams['font.family'] = 'Malgun Gothic'  # 맑은 고딕
    elif system == 'Darwin':  # macOS
        plt.rcParams['font.family'] = 'AppleGothic'
    else:  # Linux
        plt.rcParams['font.family'] = 'NanumGothic'
    
    plt.rcParams['axes.unicode_minus'] = False  # 마이너스 기호 깨짐 방지

# ==========================================
# 🎛️ 설정
# ==========================================
FREQ_MIN = 0      # 측정할 주파수 최소값
FREQ_MAX = 100    # 측정할 주파수 최대값
LOUDNESS_THRESHOLD = 0.003   # 평균 loudness 임계값
DURATION = 0.5    # 평균을 계산할 시간 (초)
# ==========================================

CHUNK = 1024
RATE = 44100
HISTORY = 80

def list_audio_devices():
    """사용 가능한 오디오 입력 장치 목록 출력"""
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

def run_monitor(device_index=None):
    # 한글 폰트 설정
    setup_korean_font()
    
    # 1. 마이크 연결
    p = pyaudio.PyAudio()
    try:
        stream = p.open(format=pyaudio.paFloat32,
                        channels=1,
                        rate=RATE,
                        input=True,
                        input_device_index=device_index,
                        frames_per_buffer=CHUNK)
    except Exception as e:
        print(f"마이크 오류: {e}")
        p.terminate()
        return

    print("\n🕵️ 0-100Hz 대역 실시간 변조 탐지 시작!")
    print(f"📊 판별 기준:")
    print(f"   - 주파수 대역: {FREQ_MIN}-{FREQ_MAX}Hz")
    print(f"   - 측정 시간: {DURATION}초")
    print(f"   - Loudness 임계값: {LOUDNESS_THRESHOLD}")
    print(f"   - 평균 >= {LOUDNESS_THRESHOLD} → 변조 목소리")
    print(f"   - 평균 < {LOUDNESS_THRESHOLD} → 정상 목소리\n")

    # 2. 화면 구성
    plt.ion()
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10))
    
    # 스펙트로그램
    spectrogram_data = np.zeros((HISTORY, int(CHUNK / 2) + 1))
    img = ax1.imshow(spectrogram_data, aspect='auto', cmap='inferno', 
                     extent=[0, RATE/2, 0, HISTORY], vmin=0, vmax=0.05)
    
    ax1.set_xlabel('Frequency (Hz)')
    ax1.set_ylabel('Time')
    ax1.set_title('Real-time Spectrogram')
    ax1.axvline(x=16000, color='cyan', linestyle='--', alpha=0.5, label='16kHz')
    ax1.axvspan(FREQ_MIN, FREQ_MAX, alpha=0.3, color='red', label=f'{FREQ_MIN}-{FREQ_MAX}Hz')
    ax1.legend(loc='upper right')
    
    # 판독 결과 텍스트
    ax2.axis('off')
    status_text = ax2.text(0.5, 0.5, 'Waiting...', fontsize=24, ha='center', va='center',
                           bbox=dict(facecolor='black', alpha=0.8, edgecolor='white'),
                           color='white', fontweight='bold')
    
    # 0.5초치 데이터 저장용 버퍼
    frames_per_duration = int((RATE / CHUNK) * DURATION)
    loudness_buffer = deque(maxlen=frames_per_duration)
    
    # 주파수 배열 미리 계산
    freqs = np.fft.rfftfreq(CHUNK, 1/RATE)
    freq_mask = (freqs >= FREQ_MIN) & (freqs <= FREQ_MAX)
    
    # 통계
    detection_count = 0
    frame_count = 0

    try:
        while plt.fignum_exists(fig.number):
            # 소리 읽기
            raw_data = stream.read(CHUNK, exception_on_overflow=False)
            audio_data = np.frombuffer(raw_data, dtype=np.float32)

            # FFT 변환
            fft_data = np.fft.rfft(audio_data)
            fft_magnitude = np.abs(fft_data) / CHUNK
            
            # 스펙트로그램 업데이트
            spectrogram_data[:-1] = spectrogram_data[1:]
            spectrogram_data[-1] = fft_magnitude
            img.set_data(spectrogram_data)
            
            # === 핵심: 0-100Hz 대역 loudness 계산 ===
            loudness_0_100 = np.mean(fft_magnitude[freq_mask])
            
            # 버퍼에 추가
            loudness_buffer.append(loudness_0_100)
            
            # 0.5초치 데이터가 쌓이면 판별
            if len(loudness_buffer) >= frames_per_duration:
                # 0.5초 평균 계산
                avg_loudness = np.mean(loudness_buffer)
                
                # 무음 체크
                overall_volume = np.max(np.abs(audio_data))
                
                if overall_volume < 0.01:
                    # 무음
                    display_text = (
                        f"SILENT\n\n"
                        f"0-100Hz Avg Loudness: {avg_loudness:.4f}\n"
                        f"Threshold: {LOUDNESS_THRESHOLD}"
                    )
                    box_color = 'gray'
                    text_color = 'white'
                    
                elif avg_loudness >= LOUDNESS_THRESHOLD:
                    # 변조 목소리
                    display_text = (
                        f"MODULATED VOICE!\n\n"
                        f"0-100Hz Avg Loudness: {avg_loudness:.4f}\n"
                        f"Threshold: {LOUDNESS_THRESHOLD}\n"
                        f"Difference: +{(avg_loudness - LOUDNESS_THRESHOLD):.4f}"
                    )
                    box_color = 'red'
                    text_color = 'yellow'
                    detection_count += 1
                    
                else:
                    # 정상 목소리
                    display_text = (
                        f"NORMAL VOICE\n\n"
                        f"0-100Hz Avg Loudness: {avg_loudness:.4f}\n"
                        f"Threshold: {LOUDNESS_THRESHOLD}\n"
                        f"Difference: {(avg_loudness - LOUDNESS_THRESHOLD):.4f}"
                    )
                    box_color = 'green'
                    text_color = 'white'
                
                # 탐지율 계산
                frame_count += 1
                detection_rate = (detection_count / frame_count * 100) if frame_count > 0 else 0
                
                display_text += f"\n\nDetection Rate: {detection_rate:.1f}%"
                
                # 텍스트 업데이트
                status_text.set_text(display_text)
                status_text.set_color(text_color)
                status_text.set_bbox(dict(facecolor=box_color, alpha=0.8, edgecolor='white'))
                
                # 콘솔 출력
                status_symbol = "🚨" if avg_loudness >= LOUDNESS_THRESHOLD else "✅"
                print(f"\r{status_symbol} Avg: {avg_loudness:.4f} | Threshold: {LOUDNESS_THRESHOLD} | Detection: {detection_rate:5.1f}%  ", end='')
            
            else:
                # 버퍼 채우는 중
                display_text = (
                    f"COLLECTING DATA...\n\n"
                    f"Collected: {len(loudness_buffer)}/{frames_per_duration} frames\n"
                    f"({len(loudness_buffer) / frames_per_duration * 100:.1f}%)"
                )
                status_text.set_text(display_text)
                status_text.set_color('white')
                status_text.set_bbox(dict(facecolor='blue', alpha=0.8, edgecolor='white'))
            
            plt.pause(0.01)

    except KeyboardInterrupt:
        print("\n\n⏹️  종료")
        print(f"\n📊 최종 통계:")
        print(f"   총 판별 횟수: {frame_count}")
        print(f"   변조 탐지 횟수: {detection_count}")
        if frame_count > 0:
            print(f"   탐지율: {detection_count/frame_count*100:.1f}%")
    finally:
        stream.stop_stream()
        stream.close()
        p.terminate()
        plt.close()

if __name__ == "__main__":
    print("\n" + "="*70)
    print("🕵️  0-100Hz Voice Modulation Detection System")
    print("="*70)
    
    # 장치 목록 출력
    input_devices = list_audio_devices()
    
    if not input_devices:
        print("\n❌ No input devices available")
        exit(1)
    
    # 장치 선택
    choice = input("\nDevice number (Enter=default): ").strip()
    
    if choice == "":
        device_index = None
        print("Using default device")
    else:
        try:
            device_index = int(choice)
            if device_index not in input_devices:
                print("❌ Invalid device number")
                exit(1)
        except ValueError:
            print("❌ Please enter a number")
            exit(1)
    
    # 실행
    try:
        run_monitor(device_index)
    except KeyboardInterrupt:
        print("\nTerminated")
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()