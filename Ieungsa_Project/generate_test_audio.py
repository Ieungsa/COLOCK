"""
테스트용 오디오 파일 생성 스크립트
- 정상 음성 (test_normal.wav): 200Hz ~ 3000Hz 랜덤 사인파
- 변조 음성 (test_modulated.wav): 0-100Hz 저주파 강화 + 고주파 노이즈
"""

import numpy as np
import wave
import struct

# 설정
SAMPLE_RATE = 44100
DURATION = 3  # 3초
NUM_SAMPLES = SAMPLE_RATE * DURATION

def generate_normal_voice():
    """정상 음성 시뮬레이션 (200Hz ~ 3000Hz)"""
    t = np.linspace(0, DURATION, NUM_SAMPLES)

    # 기본 주파수 (남성 음성: 약 120Hz, 여성 음성: 약 220Hz)
    fundamental = 200  # Hz

    # 하모닉스 추가 (자연스러운 음성)
    signal = np.zeros(NUM_SAMPLES)
    signal += 1.0 * np.sin(2 * np.pi * fundamental * t)  # 기본 주파수
    signal += 0.5 * np.sin(2 * np.pi * fundamental * 2 * t)  # 2배음
    signal += 0.3 * np.sin(2 * np.pi * fundamental * 3 * t)  # 3배음
    signal += 0.2 * np.sin(2 * np.pi * fundamental * 4 * t)  # 4배음

    # 포먼트 추가 (모음 특성)
    formant1 = 800  # Hz
    formant2 = 1200  # Hz
    signal += 0.3 * np.sin(2 * np.pi * formant1 * t)
    signal += 0.2 * np.sin(2 * np.pi * formant2 * t)

    # 약간의 고주파 노이즈 (자연스러움)
    signal += 0.05 * np.random.randn(NUM_SAMPLES)

    # 정규화
    signal = signal / np.max(np.abs(signal)) * 0.7

    return signal

def generate_modulated_voice():
    """변조 음성 시뮬레이션 (AI 변조 특징)"""
    t = np.linspace(0, DURATION, NUM_SAMPLES)

    # 정상 음성 기본
    fundamental = 180  # Hz
    signal = np.zeros(NUM_SAMPLES)
    signal += 0.8 * np.sin(2 * np.pi * fundamental * t)
    signal += 0.4 * np.sin(2 * np.pi * fundamental * 2 * t)

    # ⚠️ AI 변조 특징 1: 0-100Hz 저주파 강화
    signal += 0.5 * np.sin(2 * np.pi * 30 * t)  # 30Hz
    signal += 0.4 * np.sin(2 * np.pi * 50 * t)  # 50Hz
    signal += 0.3 * np.sin(2 * np.pi * 70 * t)  # 70Hz

    # ⚠️ AI 변조 특징 2: 100-200Hz 추가 에너지
    signal += 0.6 * np.sin(2 * np.pi * 120 * t)
    signal += 0.5 * np.sin(2 * np.pi * 150 * t)

    # ⚠️ AI 변조 특징 3: 16kHz+ 고주파 노이즈 (압축 아티팩트)
    high_freq_noise = np.random.randn(NUM_SAMPLES)
    # 16kHz 이상 필터링
    from scipy import signal as sp_signal
    b, a = sp_signal.butter(4, 16000 / (SAMPLE_RATE / 2), 'high')
    high_freq_noise = sp_signal.filtfilt(b, a, high_freq_noise)
    signal += 0.3 * high_freq_noise

    # 정규화
    signal = signal / np.max(np.abs(signal)) * 0.7

    return signal

def save_wav(filename, signal):
    """WAV 파일로 저장 (16-bit PCM)"""
    # 16-bit PCM으로 변환
    signal_int16 = np.int16(signal * 32767)

    with wave.open(filename, 'w') as wav_file:
        # 파라미터: (채널, 샘플 폭, 샘플레이트, 프레임 수, 압축 타입, 압축 이름)
        wav_file.setparams((1, 2, SAMPLE_RATE, NUM_SAMPLES, 'NONE', 'not compressed'))

        # 데이터 쓰기
        for sample in signal_int16:
            wav_file.writeframes(struct.pack('<h', sample))

    print(f"[OK] Generated: {filename}")

def main():
    print("Test audio generation...")
    print()

    # scipy 설치 확인
    try:
        import scipy
    except ImportError:
        print("scipy not installed. Generating simple version...")
        return generate_simple_version()

    # 정상 음성
    normal_signal = generate_normal_voice()
    save_wav('app/src/main/assets/test_normal.wav', normal_signal)

    # 변조 음성
    modulated_signal = generate_modulated_voice()
    save_wav('app/src/main/assets/test_modulated.wav', modulated_signal)

    print()
    print("=== File Characteristics ===")
    print()
    print("[test_normal.wav]")
    print("  - 200Hz fundamental frequency")
    print("  - Natural harmonics")
    print("  - Formants: 800Hz, 1200Hz")
    print("  - Low 0-100Hz energy")
    print()
    print("[test_modulated.wav]")
    print("  - Strong 0-100Hz energy (30, 50, 70Hz)")
    print("  - Extra 100-200Hz energy")
    print("  - 16kHz+ high frequency noise")
    print()
    print("Test in app with 'Voice Test' button!")

def generate_simple_version():
    """scipy 없이 간단한 버전 생성"""
    # 정상 음성
    t = np.linspace(0, DURATION, NUM_SAMPLES)
    normal = np.sin(2 * np.pi * 200 * t)
    normal += 0.5 * np.sin(2 * np.pi * 400 * t)
    normal = normal / np.max(np.abs(normal)) * 0.7
    save_wav('app/src/main/assets/test_normal.wav', normal)

    # 변조 음성 (저주파 강화)
    modulated = np.sin(2 * np.pi * 180 * t)
    modulated += 0.5 * np.sin(2 * np.pi * 30 * t)  # 저주파
    modulated += 0.4 * np.sin(2 * np.pi * 50 * t)
    modulated += 0.3 * np.sin(2 * np.pi * 70 * t)
    modulated += 0.2 * np.random.randn(NUM_SAMPLES)
    modulated = modulated / np.max(np.abs(modulated)) * 0.7
    save_wav('app/src/main/assets/test_modulated.wav', modulated)

    print()
    print("[OK] Simple version generated!")

if __name__ == '__main__':
    main()
