import pyaudio
import numpy as np
import matplotlib.pyplot as plt
from matplotlib import cm

# --- 설정 ---
CHUNK = 1024
RATE = 44100
HISTORY = 100

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

def run_visualizer(device_index=None):
    p = pyaudio.PyAudio()
    try:
        # 마이크 열기
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

    print("🎤 고감도 스펙트로그램 시작! (소리가 작아도 보입니다)")

    plt.ion()
    fig, ax = plt.subplots(figsize=(10, 6))
    
    spectrogram_data = np.zeros((HISTORY, int(CHUNK / 2) + 1))
    
    freq_max = RATE / 2
    
    # ★ 수정됨: vmax를 0.05로 낮춰서 작은 소리도 잘 보이게 함
    img = ax.imshow(spectrogram_data, aspect='auto', cmap='inferno', 
                    extent=[0, freq_max, 0, HISTORY], vmin=0, vmax=0.05)
    
    ax.set_xlabel('Frequency (Hz)')
    ax.set_ylabel('Time')
    ax.set_title('Real-time Voice Spectrogram')
    
    # 16kHz 기준선 (AI 판독선)
    ax.axvline(x=16000, color='cyan', linestyle='--', linewidth=2, label='AI Cutoff (16kHz)')
    ax.legend(loc='upper right')

    try:
        while plt.fignum_exists(fig.number):
            raw_data = stream.read(CHUNK, exception_on_overflow=False)
            audio_data = np.frombuffer(raw_data, dtype=np.float32)
            
            # 로그 스케일 적용 (작은 소리를 더 크게 증폭)
            fft_data = np.fft.rfft(audio_data)
            fft_magnitude = np.abs(fft_data) / CHUNK
            
            spectrogram_data[:-1] = spectrogram_data[1:]
            spectrogram_data[-1] = fft_magnitude
            
            img.set_data(spectrogram_data)
            
            # ★ 수정됨: 감도 조절
            img.set_clim(0, 0.02) 
            
            plt.pause(0.01)

    except KeyboardInterrupt:
        print("종료")
    finally:
        stream.stop_stream()
        stream.close()
        p.terminate()

if __name__ == "__main__":
    # 장치 목록 출력
    input_devices = list_audio_devices()
    
    if not input_devices:
        print("\n❌ 사용 가능한 입력 장치가 없습니다.")
    else:
        try:
            choice = input("\n분석할 장치 번호를 입력하세요 (Enter = 기본 장치): ").strip()
            
            if choice == "":
                device_index = None
            else:
                device_index = int(choice)
                if device_index not in input_devices:
                    print(f"\n❌ 잘못된 장치 번호입니다.")
                    exit(1)
            
            run_visualizer(device_index)
            
        except ValueError:
            print("\n❌ 올바른 숫자를 입력해주세요.")
        except KeyboardInterrupt:
            print("\n종료")