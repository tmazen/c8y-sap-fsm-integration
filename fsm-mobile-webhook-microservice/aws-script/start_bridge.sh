#!/bin/bash

PORT=8088
PYTHON_SCRIPT="fsm-bridge.py"

echo "--- 1. Starting Python Server on port $PORT ---"
# -u ensures logs are printed immediately to the file
nohup python3 -u $PYTHON_SCRIPT > bridge.log 2>&1 &
echo "Python server running in background (PID: $!). Logs in bridge.log"

echo "--- 2. Starting Ngrok Tunnel ---"
# We run ngrok in the background
nohup ngrok http $PORT > /dev/null 2>&1 &
sleep 5 # Wait for ngrok to initialize

echo "--- 3. Fetching Ngrok URL ---"
# Ngrok has a local API we can query to find the public URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | grep -o 'https://[^"]*ngrok-free.app')

if [ -z "$NGROK_URL" ]; then
    echo "Error: Could not get Ngrok URL. Is ngrok authenticated?"
else
    echo "---------------------------------------------------"
    echo " SUCCESS! Your bridge is live at:"
    echo " $NGROK_URL/fsm-bridge"
    echo "---------------------------------------------------"
fi
