# 온길 (OnGil)

**내비에 꽂고, 서면 칠해지는 여행 지도.** 현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작, Pleos Connect(AAOS) 앱.

그 지역에서 볼 곳을 체크하면 차가 내비에 경유지로 꽂고, 도착 즈음 내비 위에 알려 준다. 그곳에 기어 P로 3분 서면 방문이 기록되고 그 시군구가 지도에 칠해진다. 지자체가 건 세트("충주 10경")를 다 돌면 스탬프가 남는다.

| 지도 홈 | 볼 곳 고르기 | 코스 | 내비 위 알림 | 도착 · 방문 기록 | 스탬프 |
|---|---|---|---|---|---|
| ![](docs/screenshots/m0-home.png) | ![](docs/screenshots/m1-input.png) | ![](docs/screenshots/m2-recommend.png) | ![](docs/screenshots/m3-headsup.png) | ![](docs/screenshots/m4-arrival.png) | ![](docs/screenshots/m0c-stamps.png) |

## APK

- **[`apk/김대희_온길_release.apk`](apk/김대희_온길_release.apk)** (release, v2 서명, minSdk 28, 패키지 `kr.ongil.app`)

## 실행 방법

### 1. 에뮬레이터 준비
1. Android Studio에 **Pleos Connect Emulator v2.1.0** AVD를 설치하고 실행한다.
2. 앱은 차량 사용자(user 10)로 실행한다.

```bash
adb install -r apk/김대희_온길_release.apk
adb shell am start --user 10 -n kr.ongil.app/.MainActivity
```

처음 실행하면 위치·알림 권한을 묻는다. 둘 다 허용한다. 런처의 앱 목록에서 "온길"을 눌러도 된다.

### 2. 시연용 상태로 시작 (선택)

```bash
# reset = 기록 초기화, seed = 지난 여행 기록(13개 지역) 채우기, fast = 정차 3분을 6초로
adb shell am start --user 10 -n kr.ongil.app/.MainActivity --ez reset true --ez seed true --ez fast true
```

### 3. 서울 → 충주 여행 재현

에뮬레이터는 차가 움직이지 않으므로 좌표를 재생한다. 기어는 Pleos Virtual Console REST(`adb forward tcp:8088 tcp:8088`)로 바꾼다.

```bash
adb forward tcp:8088 tcp:8088
tools/demo/play_chungju.sh seoul            # 서울로 이동
# 앱: 지역 바꾸기 → 충청북도 → 충주시 → 충주시 여행 시작 → 볼 곳 체크 → 코스 만들기 → 이 코스로 출발
tools/demo/play_chungju.sh drive            # 기어 D (주행 중 간소 화면)
tools/demo/play_chungju.sh to cj-binae      # 비내섬 1km 전 → 반경 안 (내비 위 헤드업)
tools/demo/play_chungju.sh park             # 기어 P → 정차 알림 · 이야기 → 3분(fast면 6초) 뒤 방문 기록
```

전 과정을 한 번에 돌리고 스크린샷 12장을 남기려면 `tools/demo/flow_chungju.sh <출력 폴더>`.

### 4. 소스에서 빌드

```bash
cd ongil
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
export JAVA_HOME=/path/to/jdk-17
./gradlew :core:test :app:assembleDebug     # 단위 테스트 12개 + 디버그 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

릴리스 서명 키는 저장소에 없다(`ongil/keystore.properties`가 없으면 서명 없이 빌드된다).

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (AAOS, 2560×1440, 차량 사용자 user 10) |
| 빌드 | JDK 17, Android Gradle Plugin 8.8.2, compileSdk 35, minSdk 28 |
| Pleos SDK | NaviHelper 3.0.5, FusedLocation 1.1.3, Vehicle Control, TextToSpeech (Pleos Playground Maven `nexus-playground.pleos.ai`) |
| 권한 | NAVI_ROUTE, NAVI_ROUTE_SEARCH, FUSED_LOCATION·POST_NOTIFICATIONS(런타임), CAR_INFO, CAR_DRIVE, TTS_SERVICE |
| 네트워크 | 필요 없음. 명소·지도 데이터는 앱 안에 있고 서버가 없다 |
| 위치 | 에뮬레이터 내비는 재생 좌표를 받지 않는다. 온길은 FusedLocation 좌표로 판정하므로 `adb emu geo fix`(스크립트)를 쓴다 |
| 시뮬레이션 표시 | 에뮬레이터에서 만든 기록은 모두 `isSimulation = true`로 저장된다 |

**샘플 데이터 고지**: 공개 코스 435개(지자체 추천 코스 14개 포함)와 지자체 스탬프 세트 16개(14개 지역)는 시연용으로 만든 데이터다. 발행처로 보이는 지자체 부서명도 가상이고 실제 지자체와 협의한 것이 아니며, 충주·강릉·제주 마스코트는 자리 표시용 그림이다. 명소 소개는 한국관광공사 TourAPI(공공누리) 자료를 바탕으로 했다.

## 저장소 구조

```
apk/        릴리스 APK
ongil/      Android 프로젝트 (core = 순수 Kotlin 규칙·테스트, app = Pleos Connect 앱). 자세한 구조는 ongil/README.md
tools/      에뮬레이터 시연 스크립트 (좌표 재생, Virtual Console 기어)
docs/       스크린샷, 아키텍처 다이어그램 (docs/architecture/ongil-architecture.html)
```

## 라이선스

소스 MIT(`LICENSE`). 글꼴 Asta Sans는 OFL(`ongil/app/src/main/assets/licenses`). 명소 데이터는 한국관광공사 TourAPI(공공누리).
