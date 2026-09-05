# MediaPipe Hand Tracking Car

스마트폰 전면 카메라로 양손의 검지 상태를 실시간 인식하고, 인식된 손동작을 BLE 명령으로 변환하여 자동차를 제어하는 Android 앱입니다.

## 주요 기능

- Camera2 API를 이용한 전면 카메라 실시간 영상 처리
- MediaPipe Hand Landmarker를 이용한 최대 2개의 손 추적
- 손 랜드마크를 카메라 화면 위에 실시간 표시
- 왼손과 오른손 검지 상태를 자동차 주행 명령으로 변환
- Bluetooth Low Energy(BLE)로 Raspberry Pi Pico W에 명령 전송
- 손이 감지되지 않으면 0.5초 후 자동으로 `stop` 명령 전송

## 손동작과 주행 명령

| 왼손 검지 | 오른손 검지 | BLE 명령 | 동작 |
| --- | --- | --- | --- |
| 펼침 | 펼침 | `go` | 전진 |
| 접힘 | 접힘 | `back` | 후진 |
| 펼침 | 접힘 | `right` | 우회전 |
| 접힘 | 펼침 | `left` | 좌회전 |
| 손 미감지 | 손 미감지 | `stop` | 정지 |

BLE 명령은 UTF-8 문자열 뒤에 `\r\n`을 붙여 전송합니다.

## 기술 스택

- Kotlin
- Android SDK 35 (최소 SDK 25)
- Camera2 API
- MediaPipe Tasks Vision `0.10.11`
- MediaPipe Hand Landmarker
- Android Bluetooth GATT
- View Binding

## 실행 준비

1. Android Studio에서 이 프로젝트를 엽니다.
2. Gradle 동기화를 실행합니다.
3. 카메라가 있는 Android 스마트폰을 연결합니다.
4. 스마트폰에서 카메라, 블루투스 및 주변 기기 권한을 허용합니다.
5. BLE 자동차 장치를 선택하고 연결한 뒤 카메라 앞에서 손동작을 취합니다.

에뮬레이터보다 실제 카메라와 BLE를 사용할 수 있는 Android 스마트폰에서 실행하는 것을 권장합니다.

## MediaPipe 모델

손 추적 모델은 다음 위치에 포함되어 있습니다.

```text
app/src/main/assets/hand_landmarker.task
```

앱에서는 아래 이름으로 모델을 불러옵니다.

```kotlin
BaseOptions.builder()
    .setModelAssetPath("hand_landmarker.task")
    .build()
```

`HandLandmarker`가 `null`이거나 초기화에 실패한다면 다음 항목을 확인하세요.

- `hand_landmarker.task` 파일이 `app/src/main/assets`에 있는지 확인합니다.
- MediaPipe Tasks Vision 의존성이 정상적으로 다운로드됐는지 확인합니다.
- 모델 파일명이 코드의 `setModelAssetPath()` 값과 정확히 같은지 확인합니다.
- Logcat에서 `HandLandmarker setup failed` 또는 `MediaPipe 오류` 로그를 확인합니다.
- 앱을 삭제한 뒤 다시 설치하거나 Android Studio에서 Clean/Rebuild를 실행합니다.

## BLE 설정

현재 앱은 Nordic UART Service 형식의 다음 UUID를 사용합니다.

```text
Service UUID:        6E400001-B5A3-F393-E0A9-E50E24DCCA9E
RX Characteristic:  6E400002-B5A3-F393-E0A9-E50E24DCCA9E
```

다른 BLE 보드나 펌웨어를 사용한다면 `MainActivity.kt`의 서비스 UUID와 Characteristic UUID를 장치 설정에 맞게 변경해야 합니다.

## 프로젝트 구조

```text
app/src/main/
├── assets/
│   └── hand_landmarker.task
├── java/com/example/car/
│   ├── MainActivity.kt           # 카메라, 손 추적, 제스처 판정 및 BLE 명령
│   ├── OverlayView.kt            # 손 랜드마크 오버레이
│   ├── ImageUtils.kt             # 카메라 이미지 변환
│   ├── BleConnectionManager.kt   # BLE 연결 관리
│   └── DeviceListAdapter.kt      # BLE 장치 목록
└── res/layout/
    ├── activity_main.xml
    └── activity_bluetooth.xml
```

## 저장소

https://github.com/majunny/mediapipeHandTrackingCar
