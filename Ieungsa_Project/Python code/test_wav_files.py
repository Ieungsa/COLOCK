import numpy as np
import wave
import struct

def analyze_wav_file(filepath):
    """WAV 파일을 분석하여 Python 기준 결과 출력"""
    print(f"\n{'='*70}")
    print(f"분석 중: {filepath}")
    print('='*70)

    # WAV 파일 열기
    with wave.open(filepath, 'rb') as wav_file:
        sample_rate = wav_file.getframerate()
        channels = wav_file.getnchannels()
        sample_width = wav_file.getsampwidth()
        n_frames = wav_file.getnframes()

        print(f"샘플레이트: {sample_rate}Hz")
        print(f"채널: {channels}")
        print(f"비트 깊이: {sample_width * 8}bit")
        print(f"총 프레임: {n_frames}")
        print(f"재생 시간: {n_frames / sample_rate:.2f}초")

        # 오디오 데이터 읽기
        audio_data = wav_file.readframes(n_frames)

    # Short 배열로 변환 (16-bit PCM) 및 정규화
    audio_array = np.array(struct.unpack(f'{n_frames}h', audio_data), dtype=np.float32)
    # -32768 ~ +32767 범위를 -1.0 ~ +1.0으로 정규화
    audio_array = audio_array / 32768.0

    # 프레임 단위로 분석
    FRAME_SIZE = 1024
    offset = 0
    frame_count = 0
    silent_count = 0

    loudness_0_100_values = []
    loudness_100_200_values = []

    while offset + FRAME_SIZE < len(audio_array):
        frame = audio_array[offset:offset + FRAME_SIZE]

        # 볼륨 체크 (이미 정규화되었으므로 32768.0 나누기 불필요)
        volume = np.max(np.abs(frame))

        if volume > 0.01:  # 침묵이 아닐 때만
            # FFT 수행
            fft_magnitude = np.abs(np.fft.rfft(frame)) / len(frame)
            freqs = np.fft.rfftfreq(len(frame), 1/44100)

            # 0-100Hz 대역
            mask_0_100 = (freqs >= 0) & (freqs < 100)
            loudness_0_100 = np.mean(fft_magnitude[mask_0_100]) if np.any(mask_0_100) else 0

            # 100-200Hz 대역
            mask_100_200 = (freqs >= 100) & (freqs < 200)
            loudness_100_200 = np.mean(fft_magnitude[mask_100_200]) if np.any(mask_100_200) else 0

            loudness_0_100_values.append(loudness_0_100)
            loudness_100_200_values.append(loudness_100_200)

            frame_count += 1
        else:
            silent_count += 1

        offset += FRAME_SIZE

    # 통계 출력
    print(f"\n총 프레임: {frame_count + silent_count}")
    print(f"분석된 프레임: {frame_count} (침묵 제외)")
    print(f"침묵 프레임: {silent_count}")

    if loudness_0_100_values:
        print(f"\n0-100Hz 평균: {np.mean(loudness_0_100_values):.6f}")
        print(f"0-100Hz 최대: {np.max(loudness_0_100_values):.6f}")
        print(f"0-100Hz 최소: {np.min(loudness_0_100_values):.6f}")

        print(f"\n100-200Hz 평균: {np.mean(loudness_100_200_values):.6f}")
        print(f"100-200Hz 최대: {np.max(loudness_100_200_values):.6f}")
        print(f"100-200Hz 최소: {np.min(loudness_100_200_values):.6f}")

        # 변조 기준 체크
        modulated_frames = sum(1 for v in loudness_0_100_values if v >= 0.0020)
        modulated_percent = (modulated_frames / len(loudness_0_100_values)) * 100

        print(f"\n변조 특징 프레임 (0-100Hz >= 0.0020): {modulated_frames}/{len(loudness_0_100_values)} ({modulated_percent:.1f}%)")

if __name__ == "__main__":
    print("\nWAV 파일 Python 분석")

    import os
    base_path = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")

    # 두 파일 분석
    analyze_wav_file(os.path.join(base_path, "test_normal.wav"))
    analyze_wav_file(os.path.join(base_path, "test_modulated.wav"))

    print("\n" + "="*70)
    print("분석 완료")
    print("="*70)
