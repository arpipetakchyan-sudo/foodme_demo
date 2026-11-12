#!/bin/bash
# Wrapper that starts the Qase MCP server and logs startup errors.
export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"
LOG="$(dirname "$0")/qase-mcp.log"
{ echo "=== $(date) start"; echo "PATH=$PATH"; echo "node: $(command -v node) $(node -v 2>&1)"; echo "npx: $(command -v npx)"; echo "token set: ${QASE_API_TOKEN:+yes}"; } >> "$LOG" 2>&1
export npm_config_cache="$HOME/.cache/npm-qase"
exec npx -y @qase/mcp-server 2>> "$LOG"
