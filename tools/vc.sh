#!/bin/zsh
# Pleos Virtual Console REST 헬퍼 (에뮬레이터 실행 + `adb forward tcp:8088 tcp:8088` 후 사용)
# 쓰기 전에 브라우저 세션 heartbeat가 있어야 함 (없으면 503 emulator_inactive)
#   ./vc.sh window fl 50      # 운전석 창문 50% (fl/fr/rl/rr)
#   ./vc.sh wiper Low         # Off|Auto|Low|High
#   ./vc.sh soc 35            # 배터리 SOC %
#   ./vc.sh range 120000      # 주행가능거리 m
#   ./vc.sh gear Drive        # Park|Reverse|Neutral|Drive
#   ./vc.sh state             # 현재 상태 JSON
set -e
VC=${VC:-http://127.0.0.1:8088}
hb() { curl -s -o /dev/null -X POST "$VC/browser/session?client=vc-sh-$$"; }
put() { curl -s -X PUT "$VC/vehicle/$1" >/dev/null && echo "ok: $1"; }
case "$1" in
  window) hb; put "body?window_$2=$3" ;;
  wiper)  hb; put "body?front_wiper=$2" ;;
  soc)    hb; put "ev-battery?field=soc&value=$2" ;;
  range)  hb; put "odometer?remaining_range_m=$2" ;;
  odo)    hb; put "odometer?traveled_distance_km=$2" ;;
  gear)   hb; put "gear?gear=$2" ;;
  state)  curl -s "$VC/vehicle/state" | python3 -m json.tool | head -80 ;;
  *) sed -n 2,10p "$0" ;;
esac
