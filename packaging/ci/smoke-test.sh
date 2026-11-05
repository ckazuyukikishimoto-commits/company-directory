#!/usr/bin/env bash
# できあがったデスクトップ版（app-image）を 2 回起動して、
#   1. /login が 200 を返すか
#   2. 1 回目の終了時に CDS ファイルが作られるか
#   3. 2 回目の起動時間
# を確かめる（Linux 用。GitHub Actions と手元の両方で使える）。
# 使い方: packaging/ci/smoke-test.sh <アプリのフォルダ> <.env のあるフォルダ>
set -euo pipefail
APP_DIR=$1
export COMPANY_DIRECTORY_HOME=$2
export DESKTOP_SMOKE_TEST=1
LOG="$COMPANY_DIRECTORY_HOME/logs/app.log"

for run in 1 2; do
  rm -f "$LOG"
  start=$(date +%s%N)
  "$APP_DIR/bin/CompanyDirectory" > "$COMPANY_DIRECTORY_HOME/stdout-$run.log" 2>&1 &
  pid=$!
  ok=0
  for _ in $(seq 1 600); do
    if curl -s -o /dev/null -f --max-time 20 http://127.0.0.1:18080/login; then ok=1; break; fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 0.1
  done
  end=$(date +%s%N)
  if [ "$ok" != 1 ]; then
    echo "::error::${run} 回目: /login に届きませんでした"
    cat "$COMPANY_DIRECTORY_HOME/stdout-$run.log" || true
    tail -50 "$LOG" || true
    kill "$pid" 2>/dev/null || true
    exit 1
  fi
  echo "${run} 回目: /login が応答するまで $(( (end - start) / 1000000 )) ms / $(grep -o 'Started .*' "$LOG" | head -1)"
  wait "$pid"   # DESKTOP_SMOKE_TEST=1 なので 30 秒後に自分で正しく終了する
  if [ "$run" = 1 ]; then
    ls "$APP_DIR"/lib/app/*.jsa > /dev/null || { echo "::error::CDS ファイルが作られていません"; exit 1; }
    echo "CDS ファイル: $(ls -sh "$APP_DIR"/lib/app/*.jsa)"
  fi
done
