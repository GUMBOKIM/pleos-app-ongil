#!/bin/zsh
# 충주 코스 좌표 재생 (에뮬레이터용). FusedLocation이 `adb emu geo fix`를 따라간다(10/28 실측).
# 사용:
#   ./play_chungju.sh to <spotId>      # 그 명소 1.2km 밖 → 반경 안으로 순서대로 이동 (접근 알림 → 반경 진입)
#   ./play_chungju.sh at <spotId>      # 반경 안에 바로 놓기
#   ./play_chungju.sh seoul            # 서울시청
#   ./play_chungju.sh park | drive     # Virtual Console 기어 (tools/vc.sh 필요, adb forward tcp:8088 tcp:8088)
#   ./play_chungju.sh stamp <spotId>   # to → park 순서로 한 번에 (3분 또는 배속 뒤 스탬프)
set -e
HERE=${0:A:h}
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
SPOTS=$HERE/../../ongil/app/src/main/assets/spots-chungju.json

coord() { python3 -c "
import json,sys; d=json.load(open('$SPOTS'))
s=[x for x in d['spots'] if x['id']==sys.argv[1]][0]; print(s['lat'], s['lng'])" "$1"; }

fix() { $ADB emu geo fix "$2" "$1" >/dev/null; echo "geo $1,$2"; }   # geo fix는 lng lat 순서

case "$1" in
  seoul) fix 37.5665 126.9780 ;;
  at) read lat lng <<< "$(coord $2)"; fix $lat $lng ;;
  to)
    read lat lng <<< "$(coord $2)"
    # 북쪽 1.2km 밖에서 시작해 150m 간격으로 접근 (위도 0.001 ≈ 111m)
    for d in 0.0110 0.0095 0.0080 0.0065 0.0050 0.0035 0.0020 0.0008 0.0000; do
      fix $(python3 -c "print(round($lat + $d, 6))") $lng; sleep 1.5
    done ;;
  park)  $HERE/../vc.sh gear Park ;;
  drive) $HERE/../vc.sh gear Drive ;;
  stamp) $0 to $2; sleep 1; $0 park ;;
  *) sed -n 2,9p "$0" ;;
esac
