#!/bin/zsh
# 온길 2차 PT 시연 흐름(에뮬레이터): 리셋·시드 → 충주 선택 → 충주 10경 전부 → 코스 만들기 → 출발 → D → 접근 헤드업 → 반경 → P → 정차 알림 → 방문 → 도착 화면 → 홈 → 내 지도 → 여행 기록
# 스크린샷은 $OUT/<name>-s.png. 사용: tools/demo/flow_chungju.sh [outdir]
OUT=${1:-/tmp/ongil-flow}; mkdir -p $OUT; S=$OUT
HERE=$(cd "$(dirname "$0")/../.." && pwd); cd $HERE
shotw() { adb exec-out screencap -p > $S/$1.png; sips -c 1300 1682 --cropOffset 0 853 $S/$1.png --out $S/$1-c.png >/dev/null 2>&1; sips -Z 1400 $S/$1-c.png --out $S/$1-s.png >/dev/null 2>&1; rm -f $S/$1.png $S/$1-c.png; }   # 내비 위 알림까지 보이게 위로 넓게
shot() { adb exec-out screencap -p > $S/$1.png; sips -c 1210 1682 --cropOffset 90 853 $S/$1.png --out $S/$1-c.png >/dev/null 2>&1; sips -Z 1400 $S/$1-c.png --out $S/$1-s.png >/dev/null 2>&1; rm -f $S/$1.png $S/$1-c.png; }
# 화면 글자로 탭(uiautomator). 끝에 *면 접두 일치
tapt() { local t="$1"; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; local xml=$(adb shell cat /sdcard/ui.xml); local b
  if [[ "$t" == *\* ]]; then b=$(echo "$xml" | tr '>' '\n' | grep -o "text=\"${t%\*}[^\"]*\"[^/]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"'); else b=$(echo "$xml" | tr '>' '\n' | grep -o "text=\"$t\"[^/]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"'); fi
  if [ -z "$b" ]; then echo "notfound: $t"; return 1; fi
  local x1=$(echo $b | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1/'); local y1=$(echo $b | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\2/'); local x2=$(echo $b | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\3/'); local y2=$(echo $b | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\4/')
  adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); sleep ${2:-1}; }
stage() { adb shell "run-as kr.ongil.app --user 10 cat files/ongil-state.json" | python3 -c "import json,sys; d=json.load(sys.stdin); s=d.get('session'); print(s['visits'][0]['stage'] if s else 'none')"; }
adb forward tcp:8088 tcp:8088 >/dev/null
adb shell am force-stop kr.ongil.app; sleep 1
tools/demo/play_chungju.sh park; tools/demo/play_chungju.sh seoul
adb shell am start --user 10 -n kr.ongil.app/.MainActivity --ez reset true --ez seed true --ez fast true >/dev/null; sleep 5; shot m0-home
tapt "지역 바꾸기" 1.5; tapt "충청북도" 1.5; tapt "충주시" 2; shot m0f-region; tapt "충주시 여행 시작" 2; tapt "충주 10경*" 1; for n in "탄금대" "중앙탑" "충주 고구려비" "충주댐 · 충주나루" "종댕이길 출렁다리" "미륵대원지" "수안보온천" "활옥동굴" "비내섬" "목계나루"; do tapt "$n" 0.4; done; shot m1-input; tapt "코스 만들기*" 3; shot m2-recommend; tapt "이 코스로 출발" 6
tools/demo/play_chungju.sh drive; sleep 2; adb shell am start --user 10 -n kr.ongil.app/.MainActivity >/dev/null 2>&1; sleep 2; shot m3z-driving-mode
adb shell am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1; sleep 2
FIRST=$(adb shell "run-as kr.ongil.app --user 10 cat files/ongil-state.json" | python3 -c "import json,sys; print(json.load(sys.stdin)['plan']['days'][0]['stops'][0]['spotId'])"); echo first=$FIRST
(tools/demo/play_chungju.sh to $FIRST > $S/play.log 2>&1 &); sleep 5; shotw m3-headsup; sleep 11
tools/demo/play_chungju.sh park; sleep 2.5; shotw m3b-arrived-hun
for i in $(seq 1 10); do sleep 2; case "$(stage)" in STAMPED*) echo stamped; break;; esac; done; sleep 1; shotw m3c-stamped-hun
sleep 20; adb shell am start --user 10 -n kr.ongil.app/.MainActivity --es screen arrival >/dev/null 2>&1; sleep 3; shot m4-arrival
tapt "오늘은 여기까지" 3; shot m0g-home-after; tapt "내 지도" 2; shot m0b-mymap; tapt "충주*" 2; shot m5-card; echo "flow-done → $OUT"
