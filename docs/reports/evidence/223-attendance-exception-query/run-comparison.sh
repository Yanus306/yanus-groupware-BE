#!/usr/bin/env bash
set -eu

evidence_dir="$(cd "$(dirname "$0")" && pwd)"
candidate="$(git -C "$evidence_dir" rev-parse --show-toplevel)"
output_dir="${1:?usage: bash run-comparison.sh /path/to/output [Gradle options]}"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
baseline="$(git -C "$candidate" rev-parse --verify "${YANUS_223_BASELINE_COMMIT:-210b46324bef885350db6c8e64ee38ab3c1c581b}^{commit}")"
export YANUS_223_BASELINE_COMMIT="$baseline"

scratch="$(mktemp -d "${TMPDIR:-/tmp}/yanus-223-comparison.XXXXXXXX")"
trap 'python3 -c '"'"'from pathlib import Path; from shutil import rmtree; import sys; p=Path(sys.argv[1]); assert p.name.startswith("yanus-223-comparison.") and not p.is_symlink(); rmtree(p)'"'"' "$scratch"' EXIT
mkdir "$scratch/baseline" "$scratch/probe"
git -C "$candidate" archive "$baseline" | tar -x -C "$scratch/baseline"
cp "$evidence_dir/ExceptionQueryBaselineProbe.java" "$evidence_dir/probe.init.gradle" "$scratch/probe/"

fingerprint() {
    python3 - "$1" <<'PY'
import hashlib
import sys
from pathlib import Path
root = Path(sys.argv[1])
paths = sorted(
    [p for folder in ('src/main', 'gradle') for p in (root / folder).rglob('*') if p.is_file()]
    + [root / 'build.gradle', root / 'settings.gradle']
)
h = hashlib.sha256()
for path in paths:
    h.update(str(path.relative_to(root)).encode() + b'\0' + hashlib.sha256(path.read_bytes()).digest())
print(h.hexdigest())
PY
}

run_variant() {
    (
        cd "$1"
        source_dir="$1"
        variant="$2"
        export GITHUB_SHA="$3" YANUS_223_VARIANT="$variant" YANUS_223_PROBE_DIR="$scratch/probe"
        shift 3
        export YANUS_223_HTTP_BENCHMARK=true YANUS_223_SOURCE_FINGERPRINT="$(fingerprint "$source_dir")"
        if ! ./gradlew --console=plain "$@" -I "$scratch/probe/probe.init.gradle" exceptionQueryBaselineProbe > "$output_dir/benchmark-$variant.log" 2>&1; then
            tail -70 "$output_dir/benchmark-$variant.log"
            exit 1
        fi
        cp "$scratch/probe/baseline.json" "$output_dir/benchmark-$variant.json"
        printf 'Saved %s results in %s\n' "$variant" "$output_dir"
    )
}

shift
run_variant "$scratch/baseline" baseline "$baseline" "$@"
run_variant "$candidate" optimized "$(git -C "$candidate" rev-parse HEAD)" "$@"
