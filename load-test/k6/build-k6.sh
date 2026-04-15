#!/bin/bash
# xk6-sse 확장이 포함된 k6 바이너리를 빌드한다.
# Docker가 필요하며, 현재 디렉토리에 k6 바이너리가 생성된다.
#
# 사용법: ./build-k6.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

docker run --rm -u "$(id -u):$(id -g)" \
  -v "${SCRIPT_DIR}:/xk6" \
  grafana/xk6 build \
  --with github.com/phymbert/xk6-sse

echo "k6 binary built at: ${SCRIPT_DIR}/k6"
echo "Verify: ${SCRIPT_DIR}/k6 version"
