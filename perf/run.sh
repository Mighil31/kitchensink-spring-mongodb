#!/usr/bin/env bash
# Runs the full performance baseline against one target and saves every run's console summary.
#
#   perf/run.sh legacy                 # full run (~45 min)
#   perf/run.sh legacy quick           # dry run (~2 min), results go to results/<name>-quick
#   perf/run.sh legacy knee            # only the knee levels not run yet (keeps existing results)
#   PERF_ARGS="-DbaseUrl=http://localhost:8081/rest -Duser=api" PERF_PASSWORD=... perf/run.sh v2
#
# Stage A: each simulation once as a discarded warm-up, then 3 measured runs (median reported).
# Stage B: the knee. MixedSloSimulation at increasing rates until a level breaks the criteria.
# Then: python3 scripts/scorecard.py save-perf --name <name> --label "..."
set -euo pipefail

name=${1:?usage: run.sh <legacy|v2> [quick]}
mode=${2:-full}
cd "$(dirname "$0")"

if [[ $mode == quick ]]; then
  out=results/$name-quick; duration=10; runs=1; warmup=0; rate=10
  knee_rates=(10 20); knee_duration=10
else
  out=results/$name; duration=180; runs=3; warmup=1; rate=50
  knee_rates=(50 100 200 400 800 1600 3200 6400); knee_duration=60
fi
KNEE_P95_MS=300
KNEE_ERROR_PCT=1

read -r -a extra <<< "${PERF_ARGS:-}"
if [[ $mode != knee ]]; then rm -rf "$out"; fi
mkdir -p "$out"
mvn -q test-compile

run() { # simulation rate duration logfile
  echo "$(date +%H:%M:%S)  $1 at $2 req/s for $3 s -> $4"
  mvn -q gatling:test -Dgatling.simulationClass="io.github.mighil31.kitchensink.perf.$1" \
      -Drate="$2" -Dduration="$3" "${extra[@]}" > "$4" 2>&1 || true
  sed -i "s#$HOME#~#g" "$4"   # logs are committed; keep local paths (and the user name) out
  grep -q -- '---- Global Information' "$4" || { echo "  run failed, see $4"; exit 1; }
}

[[ $mode == knee ]] || for sim in RegistrationSimulation LookupSimulation MixedSloSimulation; do
  for ((i = 1; i <= warmup; i++)); do run "$sim" "$rate" "$duration" "$out/$sim-warmup.log"; done
  for ((i = 1; i <= runs; i++)); do run "$sim" "$rate" "$duration" "$out/$sim-run$i.log"; done
done

for level in "${knee_rates[@]}"; do
  if [[ $mode == knee && -f $out/knee-$level.log ]]; then
    python3 ../scripts/scorecard.py check-level "$out/knee-$level.log" --p95 "$KNEE_P95_MS" --errors "$KNEE_ERROR_PCT" >/dev/null && continue || break
  fi
  run MixedSloSimulation "$level" "$knee_duration" "$out/knee-$level.log"
  if ! python3 ../scripts/scorecard.py check-level "$out/knee-$level.log" \
        --p95 "$KNEE_P95_MS" --errors "$KNEE_ERROR_PCT"; then
    echo "  knee passed: $level req/s broke p95 <= ${KNEE_P95_MS} ms / errors <= ${KNEE_ERROR_PCT}%"
    break
  fi
done

echo "$(date +%H:%M:%S)  done. Logs in perf/$out"
