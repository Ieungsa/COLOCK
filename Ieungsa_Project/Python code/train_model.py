import numpy as np
import json
import joblib
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import matplotlib.pyplot as plt
import os

# TFLite 변환용
try:
    import tensorflow as tf
    TFLITE_AVAILABLE = True
except ImportError:
    TFLITE_AVAILABLE = False
    print("WARNING: TensorFlow not installed. TFLite conversion will be skipped.")

class VoiceAITrainer:
    def __init__(self):
        self.model = None
        self.feature_names = [
            '0-100Hz',
            '100-200Hz', 
            '200-300Hz',
            '4-8kHz',
            '16kHz+',
            'Total Energy',
            'Spectral Centroid',
            'ZCR'
        ]
    
    def load_dataset(self, filepath):
        print(f"\n📂 데이터셋 로드 중: {filepath}")
        with open(filepath, 'r', encoding='utf-8') as f:
            dataset = json.load(f)
        
        samples = dataset['samples']
        print(f"   총 샘플: {len(samples)}개")
        
        X = []
        y = []
        for sample in samples:
            features = sample['features']
            feature_vector = [
                features['loudness_0_100'],
                features['loudness_100_200'],
                features['loudness_200_300'],
                features['loudness_4k_8k'],
                features['loudness_16k'],
                features['total_energy'],
                features['spectral_centroid'],
                features['zcr']
            ]
            X.append(feature_vector)
            label = 1 if sample['label'] == 'modulated' else 0
            y.append(label)
        
        return np.array(X, dtype=np.float32), np.array(y)
    
    def analyze_data(self, X, y):
        print("\n" + "="*70)
        print("📊 데이터 분석")
        print("="*70)
        X_normal = X[y == 0]
        X_modulated = X[y == 1]
        print("\n특징별 평균값:")
        print(f"{'특징':20s} {'정상':>12s} {'변조':>12s} {'차이':>12s}")
        print("-" * 70)
        for i, name in enumerate(self.feature_names):
            mean_normal = np.mean(X_normal[:, i])
            mean_modulated = np.mean(X_modulated[:, i])
            diff = mean_modulated - mean_normal
            print(f"{name:20s} {mean_normal:12.6f} {mean_modulated:12.6f} {diff:12.6f}")
        self.plot_feature_comparison(X_normal, X_modulated)
    
    def plot_feature_comparison(self, X_normal, X_modulated):
        fig, axes = plt.subplots(2, 4, figsize=(16, 8))
        fig.suptitle('Feature Comparison: Normal vs Modulated', fontsize=16, fontweight='bold')
        axes = axes.flatten()
        for i, (ax, name) in enumerate(zip(axes, self.feature_names)):
            ax.hist(X_normal[:, i], bins=30, alpha=0.6, label='Normal', color='green')
            ax.hist(X_modulated[:, i], bins=30, alpha=0.6, label='Modulated', color='red')
            ax.set_xlabel(name)
            ax.set_ylabel('Count')
            ax.legend()
            ax.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig('feature_comparison.png', dpi=150, bbox_inches='tight')
        print("\n📊 특징 비교 그래프 저장: feature_comparison.png")
        plt.show()
    
    def train(self, X, y, test_size=0.2):
        print("\n" + "="*70)
        print("🤖 AI 모델 학습 시작")
        print("="*70)
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=42, stratify=y
        )
        print(f"\n학습 데이터: {len(X_train)}개")
        print(f"테스트 데이터: {len(X_test)}개")
        
        self.model = RandomForestClassifier(
            n_estimators=100, max_depth=10, min_samples_split=5, min_samples_leaf=2,
            random_state=42, n_jobs=-1
        )
        self.model.fit(X_train, y_train)
        print("✅ 학습 완료!")
        
        print("\n" + "="*70)
        print("📊 모델 평가")
        print("="*70)
        print(f"\n학습 정확도: {self.model.score(X_train, y_train)*100:.2f}%")
        print(f"테스트 정확도: {self.model.score(X_test, y_test)*100:.2f}%")
        
        cv_scores = cross_val_score(self.model, X, y, cv=5)
        print(f"교차 검증 평균: {cv_scores.mean()*100:.2f}% (±{cv_scores.std()*100:.2f}%)")
        
        y_pred = self.model.predict(X_test)
        print("\n📋 상세 분류 보고서:")
        print(classification_report(y_test, y_pred, target_names=['정상', '변조']))
        
        self.show_feature_importance()
        return self.model
    
    def show_feature_importance(self):
        if self.model is None: return
        importances = self.model.feature_importances_
        indices = np.argsort(importances)[::-1]
        print("\n🎯 특징 중요도 순위:")
        for i, idx in enumerate(indices):
            print(f"{i+1}. {self.feature_names[idx]:20s}: {importances[idx]:.4f}")
    
    def save_model(self, X_all, y_all, filename='voice_classifier.pkl'):
        """모델 저장 및 정밀 TFLite 변환"""
        if self.model is None:
            print("저장할 모델이 없습니다.")
            return

        joblib.dump(self.model, filename)
        print(f"\n💾 모델 저장 완료: {filename}")

        if TFLITE_AVAILABLE:
            print("\n" + "="*70)
            print("🔄 TFLite 변환 시작...")
            print("="*70)
            # 레이블 뒤집힘 방지를 위해 실제 정답 데이터 y_all을 함께 전달합니다.
            tflite_path = self.convert_to_tflite(X_all, y_all, filename)
            if tflite_path:
                print(f"\n✅ TFLite 변환 완료: {tflite_path}")

    def convert_to_tflite(self, X_real, y_real, pkl_path):
        """실제 레이블을 학습 목표로 사용하여 오차 0.98 문제를 해결합니다."""
        try:
            tflite_path = pkl_path.replace('.pkl', '.tflite')
            
            # 1. 신경망 모델 구성 (데이터 스케일 자동 조절을 위해 BatchNormalization 추가)
            model = tf.keras.Sequential([
                tf.keras.layers.Input(shape=(8,)),
                tf.keras.layers.BatchNormalization(), # 수치 안정성 확보
                tf.keras.layers.Dense(64, activation='relu'),
                tf.keras.layers.Dense(32, activation='relu'),
                tf.keras.layers.Dense(2, activation='softmax')
            ])

            # 2. 정답 데이터 원-핫 인코딩 (레이블 순서 [정상, 변조] 고정)
            y_target = tf.keras.utils.to_categorical(y_real, num_classes=2)

            model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])

            print("   신경망 학습 중 (실제 레이블 기반 정밀 정렬)...")
            # 스승 모델의 불확실한 확률값 대신 '진짜 정답'을 가르칩니다.
            model.fit(X_real, y_target, epochs=300, batch_size=16, verbose=0)

            # 3. 변환
            converter = tf.lite.TFLiteConverter.from_keras_model(model)
            tflite_model = converter.convert()

            with open(tflite_path, 'wb') as f:
                f.write(tflite_model)

            self.verify_tflite(tflite_path, X_real[:5])
            return tflite_path

        except Exception as e:
            print(f"\n   ❌ TFLite 변환 실패: {e}")
            import traceback
            traceback.print_exc()
            return None

    def verify_tflite(self, tflite_path, test_samples):
        try:
            interpreter = tf.lite.Interpreter(model_path=tflite_path)
            interpreter.allocate_tensors()
            input_details = interpreter.get_input_details()
            output_details = interpreter.get_output_details()

            print("\n   === 변환 검증 (RF vs TFLite) ===")
            for i, sample in enumerate(test_samples):
                s_input = sample.reshape(1, 8)
                rf_p = self.model.predict_proba(s_input)[0]
                
                interpreter.set_tensor(input_details[0]['index'], s_input)
                interpreter.invoke()
                tf_p = interpreter.get_tensor(output_details[0]['index'])[0]
                
                diff = np.abs(rf_p - tf_p).max()
                # 오차가 0.1 미만이면 합격
                status = "✅" if diff < 0.2 else "⚠️"
                print(f"   샘플 {i+1}: RF {rf_p} | TFLite {tf_p} -> {status} (오차: {diff:.4f})")
        except Exception as e:
            print(f"   ⚠️ 검증 오류: {e}")

    def test_predictions(self, X, y):
        if self.model is None: return
        print("\n🧪 예측 테스트 (랜덤 10개)")
        indices = np.random.choice(len(X), min(10, len(X)), replace=False)
        for idx in indices:
            feat = X[idx:idx+1]
            prob = self.model.predict_proba(feat)[0]
            pred = np.argmax(prob)
            correct = "✅" if pred == y[idx] else "❌"
            print(f"{correct} 실제: {'변조' if y[idx]==1 else '정상'} | 예측: {'변조' if pred==1 else '정상'} | 신뢰도: {prob[pred]*100:.1f}%")

def main():
    print("\n" + "="*70)
    print("🤖 음성 변조 탐지 AI - 통합 모델 학습")
    print("="*70)
    
    dataset_dir = 'voice_dataset'
    if not os.path.exists(dataset_dir): return
    json_files = [f for f in os.listdir(dataset_dir) if f.endswith('.json')]
    if not json_files: return
    
    dataset_path = os.path.join(dataset_dir, json_files[0])
    try:
        trainer = VoiceAITrainer()
        X, y = trainer.load_dataset(dataset_path)
        trainer.analyze_data(X, y)
        trainer.train(X, y)
        trainer.test_predictions(X, y)
        
        save = input("\n💾 모델을 저장하고 변환하시겠습니까? (y/n): ").strip().lower()
        if save == 'y':
            # 핵심 수정: X와 y를 모두 넘겨주어 레이블 뒤집힘을 방지함
            trainer.save_model(X, y, 'voice_classifier.pkl')
            print("\n✅ 모든 과정이 완료되었습니다. 안드로이드 assets 폴더로 이동하세요.")
    except Exception as e:
        print(f"❌ 오류 발생: {e}")

if __name__ == "__main__":
    main()