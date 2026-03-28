#!/usr/bin/env bash
set -euo pipefail

PATTERN='RetentionJob|TransientLogCache'
TARGETS=(event-log-api/src)

matches=()
for target in "${TARGETS[@]}"; do
  if [[ -d "$target" ]]; then
    if output=$(rg -n --color=never -e "$PATTERN" "$target"); then
      matches+=("$output")
    fi
  fi
done

if [[ ${#matches[@]} -gt 0 ]]; then
  echo "Legacy RetentionJob/TransientLogCache references detected:"
  printf '%s\n' "${matches[@]}"
  exit 1
fi

echo "No legacy RetentionJob/TransientLogCache references found in runtime source."
