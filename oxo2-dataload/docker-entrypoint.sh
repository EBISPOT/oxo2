#!/bin/bash

set -euo pipefail

mkdir -p /mnt/oxo/data /mnt/oxo/logs

exec "$@"
