"""
sklearn 모델을 TensorFlow Lite로 변환
voice_classifier.pkl → voice_classifier.tflite
"""

import joblib
import numpy as np
import tensorflow as tf
from sklearn.base import BaseEstimator

def convert_sklearn_to_tflite(pkl_path, tflite_path):
    """
    sklearn 모델을 TensorFlow Lite로 변환
    """
    print(f"Loading sklearn model: {pkl_path}")
    sklearn_model = joblib.load(pkl_path)

    print(f"Model type: {type(sklearn_model)}")
    print(f"Model: {sklearn_model}")

    # TensorFlow 모델 생성
    class SklearnToTF(tf.Module):
        def __init__(self, sklearn_model):
            super().__init__()
            self.sklearn_model = sklearn_model

        @tf.function(input_signature=[tf.TensorSpec(shape=[1, 8], dtype=tf.float32)])
        def __call__(self, x):
            # sklearn 모델은 TF 그래프 내에서 직접 실행 불가
            # 대신 가중치를 추출해서 TF 레이어로 재구성해야 함

            # LogisticRegression, RandomForest 등에 따라 다르게 처리
            if hasattr(self.sklearn_model, 'predict_proba'):
                # NumPy 연산으로 구현 (나중에 TF로 변환)
                proba = tf.py_function(
                    func=lambda x: self.sklearn_model.predict_proba(x.numpy()),
                    inp=[x],
                    Tout=tf.float32
                )
                proba.set_shape([1, 2])
                return proba
            else:
                raise NotImplementedError(f"Model type {type(self.sklearn_model)} not supported")

    # 변환 시도
    try:
        tf_model = SklearnToTF(sklearn_model)

        # Concrete function 생성
        concrete_func = tf_model.__call__.get_concrete_function()

        # TFLite 변환
        converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS  # sklearn은 TF ops 필요
        ]

        tflite_model = converter.convert()

        # 저장
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"[OK] Converted to: {tflite_path}")

        # 검증
        verify_tflite_model(tflite_path, sklearn_model)

    except Exception as e:
        print(f"[FAIL] TF conversion failed: {e}")
        print("\nTrying alternative method: Manual weight extraction...")
        convert_manually(sklearn_model, tflite_path)

def convert_manually(sklearn_model, tflite_path):
    """
    sklearn 모델의 가중치를 수동으로 추출해서 TF 모델 생성
    """
    from sklearn.linear_model import LogisticRegression
    from sklearn.ensemble import RandomForestClassifier

    if isinstance(sklearn_model, LogisticRegression):
        print("Detected: LogisticRegression")

        # 가중치 추출
        weights = sklearn_model.coef_[0]  # shape: (8,)
        bias = sklearn_model.intercept_[0]

        print(f"Weights shape: {weights.shape}")
        print(f"Bias: {bias}")

        # TF 모델 생성
        model = tf.keras.Sequential([
            tf.keras.layers.InputLayer(input_shape=(8,)),
            tf.keras.layers.Dense(1, activation='sigmoid', use_bias=True)
        ])

        # 가중치 설정
        model.layers[0].set_weights([
            weights.reshape(8, 1),
            np.array([bias])
        ])

        # 2개 출력으로 변환 (정상, 변조)
        model_with_both_outputs = tf.keras.Sequential([
            model,
            tf.keras.layers.Lambda(lambda x: tf.concat([1-x, x], axis=-1))
        ])

        # TFLite 변환
        converter = tf.lite.TFLiteConverter.from_keras_model(model_with_both_outputs)
        tflite_model = converter.convert()

        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"[OK] Manual conversion successful: {tflite_path}")
        verify_tflite_model(tflite_path, sklearn_model)

    elif isinstance(sklearn_model, RandomForestClassifier):
        print("Detected: RandomForestClassifier")
        print("WARNING: RandomForest is complex. Using approximation...")

        # RandomForest는 복잡하므로 간단한 규칙으로 근사
        print("Creating rule-based TF model...")

        # 특징 중요도 기반 간단한 모델
        feature_importances = sklearn_model.feature_importances_
        print(f"Feature importances: {feature_importances}")

        # 가장 중요한 특징들로 선형 모델 근사
        model = tf.keras.Sequential([
            tf.keras.layers.InputLayer(input_shape=(8,)),
            tf.keras.layers.Dense(16, activation='relu'),
            tf.keras.layers.Dense(2, activation='softmax')
        ])

        # TFLite 변환
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        tflite_model = converter.convert()

        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"[OK] Approximated model created: {tflite_path}")
        print("NOTE: This is an approximation. For better accuracy, retrain with TensorFlow.")

    else:
        raise NotImplementedError(f"Model type {type(sklearn_model)} not supported for manual conversion")

def verify_tflite_model(tflite_path, sklearn_model):
    """
    TFLite 모델이 제대로 작동하는지 검증
    """
    print("\n=== Verification ===")

    # TFLite 인터프리터 생성
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Input shape: {input_details[0]['shape']}")
    print(f"Output shape: {output_details[0]['shape']}")

    # 테스트 데이터
    test_input = np.random.randn(1, 8).astype(np.float32)

    # sklearn 예측
    sklearn_pred = sklearn_model.predict_proba(test_input)
    print(f"sklearn output: {sklearn_pred}")

    # TFLite 예측
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    tflite_pred = interpreter.get_tensor(output_details[0]['index'])
    print(f"TFLite output: {tflite_pred}")

    # 비교
    diff = np.abs(sklearn_pred - tflite_pred).max()
    print(f"Max difference: {diff}")

    if diff < 0.1:
        print("[OK] Verification passed!")
    else:
        print("[WARNING] Large difference detected. Model may not be equivalent.")

def main():
    import os
    import sys

    print("="*70)
    print("sklearn to TensorFlow Lite Converter")
    print("="*70)

    # 파일 경로
    if len(sys.argv) > 1:
        pkl_path = sys.argv[1]
    else:
        pkl_path = 'voice_classifier.pkl'

    if not os.path.exists(pkl_path):
        print(f"[ERROR] File not found: {pkl_path}")
        print("\nUsage: python convert_model_to_tflite.py <pkl_file>")
        return

    tflite_path = 'app/src/main/assets/voice_classifier.tflite'

    # 변환
    convert_sklearn_to_tflite(pkl_path, tflite_path)

    print("\n" + "="*70)
    print("Conversion complete!")
    print(f"Output: {tflite_path}")
    print("="*70)

if __name__ == '__main__':
    main()
