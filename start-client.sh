#!/bin/bash
cd /Users/alsk/Desktop/git/VibeCoding/SanguoshaWebArena/sanguosha-webarena-client
npx vite --port 5173 &
disown
echo "PID: $!"