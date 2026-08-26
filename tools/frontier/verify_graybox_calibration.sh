#!/usr/bin/env bash
# Verify that the checked-in yearly fixture still describes the active Python
# source, then run the Java-side per-seed conformance gate without rewriting it.
set -euo pipefail

usage() {
    echo "usage: $0 --reference-root PATH" >&2
}

if [[ $# -ne 2 || "$1" != "--reference-root" ]]; then
    usage
    exit 64
fi

reference_root=$2
if [[ ! -f "$reference_root/simulation/world.py" ]]; then
    echo "reference simulation is unavailable at $reference_root" >&2
    exit 66
fi

script_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
python_bin=${PYTHON_BIN:-python3.11}
if ! command -v "$python_bin" >/dev/null 2>&1; then
    echo "required Python interpreter is unavailable: $python_bin" >&2
    exit 69
fi

"$python_bin" "$script_root/tools/frontier/generate_graybox_calibration_envelope.py" \
    --reference-root "$reference_root" \
    --output "$script_root/docs/frontier-reference-graybox-calibration.json" \
    --check

cd "$script_root"
./gradlew :pale-mirror-domain:test --tests 'io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxCalibrationTest'
