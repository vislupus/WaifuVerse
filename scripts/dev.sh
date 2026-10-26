#!/usr/bin/env bash
# Everything at once: build + install + launch + live logs.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
"$ROOT/scripts/run.sh"
exec "$ROOT/scripts/logs.sh"
