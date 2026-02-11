# Mind-Shield AI - 보이스피싱 방어 앱

DACON 경진대회(236666)를 위한 온디바이스 MLLM 기반 지능형 심리 방어 에이전트 MVP

## 프로젝트 개요

사용자의 심리적 상태와 화면의 시나리오 의도를 실시간으로 읽어내어 골든타임에 개입하는 보이스피싱 방어 애플리케이션입니다.

### 핵심 기능

1. **실시간 시나리오 브레이커**
   - Accessibility Service를 통해 화면 텍스트 추출
   - 현재 상황이 '검찰 사칭', '구인 사기' 등인지 실시간 판단

2. **지능형 키워드 감지**
   - 보이스피싱 관련 위험 키워드 실시간 모니터링
   - 검찰, 경찰, 금융감독원, 송금, 계좌이체 등 주요 키워드 탐지

3. **로그 기반 위협 알림**
   - 의심스러운 패턴 발견 시 Logcat에 경고 출력
   - 개발자 및 테스터가 실시간 모니터링 가능

## 빌드 및 설치

### 빌드 성공 완료
```bash
cd C:\Users\Rhee\Desktop\KMOU\Ieungsa
gradlew.bat assembleDebug
```

**생성된 APK 위치:**
```
C:\Users\Rhee\Desktop\KMOU\Ieungsa\app\build\outputs\apk\debug\app-debug.apk
```

### 에뮬레이터/기기에 설치

#### Android Studio 사용
1. Run 버튼(▶️) 클릭
2. 에뮬레이터 또는 실제 기기 선택
3. 자동으로 설치 및 실행

##  ## ADB 사용
```bash
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 사용 방법

### 1. 앱 실행
- 앱을 실행하면 "Mind-Shield AI" 메인 화면이 나타납니다

### 2. Accessibility 서비스 활성화
1. "보안 서비스 활성화하기" 버튼 클릭
2. 설정 화면에서 "Mind Shield AI" 찾기
3. 서비스 활성화

### 3. 모니터링 확인
- Android Studio의 Logcat에서 "MIND_SHIELD" 태그로 필터링
- 화면에 위험 키워드 감지 시 실시간 로그 출력

## 기술 스택

### Android
- **언어**: Kotlin
- **UI**: Jetpack Compose
- **최소 SDK**: 26 (Android 8.0)
- **타겟 SDK**: 35 (Android 15)

### 주요 라이브러리
- **Jetpack Compose**: 현대적인 선언형 UI
- **Coroutines**: 비동기 처리
- **Google AI SDK**: 향후 Gemini API 연동 예정
- **Accessibility Service**: 화면 텍스트 추출

### Gradle 설정
- Gradle: 8.11
- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21

## 현재 구현 상태

### ✅ 완료된 기능
- [x] 기본 UI 구성 (Jetpack Compose)
- [x] Accessibility Service 설정
- [x] 화면 텍스트 추출 기능
- [x] 키워드 기반 위험 감지
- [x] 로그 기반 알림 시스템
- [x] APK 빌드 성공

### 🚧 진행 중/향후 개선 사항
- [ ] 온디바이스 ML Kit Gemini Nano 적용
  - 현재 ML Kit GenAI Prompt API는 공개되지 않음
  - Google Play Services Developer Preview 필요
- [ ] Google AI SDK를 통한 온라인 Gemini API 연동
  - API 키 설정 필요
- [ ] 실시간 UI 경고 팝업
- [ ] 패닉 제스처 탐지 (가속도계 활용)
- [ ] 음성 딥페이크 감지
- [ ] 사용자 행동 패턴 분석

## 파일 구조

```
Ieungsa/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ieungsa/myapplication/
│   │   │   │   ├── MainActivity.kt              # 메인 액티비티
│   │   │   │   ├── MyAccessibilityService.kt    # 접근성 서비스
│   │   │   │   └── ui/theme/                    # UI 테마
│   │   │   ├── res/
│   │   │   │   ├── values/strings.xml
│   │   │   │   └── xml/accessibility_service_config.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── build/outputs/apk/debug/
│       └── app-debug.apk                         # 생성된 APK
├── gradle/
│   └── libs.versions.toml                        # 의존성 버전 관리
├── build.gradle.kts
└── settings.gradle.kts
```

## 권한 설명

### 필수 권한
- **SYSTEM_ALERT_WINDOW**: 화면 위에 경고 표시
- **INTERNET**: 향후 온라인 AI API 사용
- **BIND_ACCESSIBILITY_SERVICE**: 화면 텍스트 읽기

## 보안 및 프라이버시

- 모든 화면 분석은 로컬에서 수행
- 외부 서버로 데이터 전송 없음 (온디바이스 처리)
- 대한민국 개인정보보호법(PIPA) 준수

## 트러블슈팅

### ML Kit 라이브러리 에러
- ML Kit GenAI Prompt API는 아직 공개 Maven에 배포되지 않음
- Google Play Services Developer Preview 필요
- 현재는 키워드 기반 감지로 대체 구현

### Accessibility 권한 에러
- 설정 > 접근성에서 수동으로 활성화 필요
- Android 보안 정책상 자동 활성화 불가

## 개발자 정보

- **프로젝트명**: Mind-Shield AI
- **경진대회**: DACON 236666
- **빌드 날짜**: 2026-01-23
- **빌드 상태**: ✅ SUCCESS

## 라이선스

이 프로젝트는 DACON 경진대회 출품작입니다.
