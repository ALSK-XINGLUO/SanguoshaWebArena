#!/bin/bash

# ============================================
# Sanguosha-WebArena 启动后端服务器
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "========================================"
echo "  启动后端服务器 (Spring Boot)..."
echo "========================================"

cd "$SCRIPT_DIR/sanguosha-webarena-server"

# 在后台运行 Maven
mvn spring-boot:run -q > /tmp/sanguosha-server.log 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > /tmp/sanguosha-server.pid
echo "后端 PID: $SERVER_PID"
echo "日志: tail -f /tmp/sanguosha-server.log"

# 等待后端启动完成 (最长等待 60 秒)
echo "等待后端启动..."
for i in $(seq 1 60); do
  if curl -s http://localhost:8080/api/auth/login > /dev/null 2>&1; then
    echo "后端启动成功! (http://localhost:8080)"
    exit 0
  fi
  if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo "[错误] 后端进程异常退出，查看日志:"
    tail -20 /tmp/sanguosha-server.log
    exit 1
  fi
  sleep 1
done

echo "[超时] 后端启动超时，请查看日志: tail -f /tmp/sanguosha-server.log"
exit 1