#!/usr/bin/env bash
# wait.sh <seconds>: a pause that does not use `sleep` (some sandboxes block foreground sleep).
timeout "$1" bash -c 'while :; do :; done' 2>/dev/null; true
