#!/bin/bash
# xk6-sse 확장이 포함된 k6 바이너리를 빌드한다.
# Docker가 필요하며, 현재 디렉토리에 k6 바이너리가 생성된다.
#
# 사용법: ./build-k6.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 호스트 OS/아키텍처 감지하여 크로스 컴파일
HOST_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
HOST_ARCH=$(uname -m)

case "$HOST_ARCH" in
  x86_64) HOST_ARCH="amd64" ;;
  arm64|aarch64) HOST_ARCH="arm64" ;;
esac

echo "Building k6 for ${HOST_OS}/${HOST_ARCH}..."

docker run --rm -u "$(id -u):$(id -g)" \
  -e GOOS="${HOST_OS}" \
  -e GOARCH="${HOST_ARCH}" \
  -v "${SCRIPT_DIR}:/xk6" \
  grafana/xk6 build \
  --with github.com/phymbert/xk6-sse

echo "k6 binary built at: ${SCRIPT_DIR}/k6"
echo "Verify: ${SCRIPT_DIR}/k6 version"
