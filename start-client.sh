#!/bin/bash

# ============================================
# Sanguosha-WebArena 启动前端开发服务器
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "========================================"
echo "  启动前端开发服务器 (Vite)..."
echo "========================================"

cd "$SCRIPT_DIR/sanguosha-webarena-client"

# 先确保依赖已安装
if [ ! -d "node_modules" ]; then
  echo "安装前端依赖..."
  npm install
fi

npm run dev > /tmp/sanguosha-client.log 2>&1 &
CLIENT_PID=$!
echo "$CLIENT_PID" > /tmp/sanguosha-client.pid
echo "前端 PID: $CLIENT_PID"
echo "日志: tail -f /tmp/sanguosha-client.log"

# 等待前端启动
sleep 3
echo "前端启动成功! (http://localhost:5173)"