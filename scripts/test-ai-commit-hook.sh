#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat > "$TMP_DIR/python3" <<'SH'
#!/usr/bin/env sh
echo "Python was not found" >&2
exit 127
SH
chmod +x "$TMP_DIR/python3"

cat > "$TMP_DIR/curl" <<'SH'
#!/usr/bin/env sh
cat >/dev/null
printf '%s\n' '{"choices":[{"message":{"content":"chore(test): 自动生成提交信息"}}]}'
SH
chmod +x "$TMP_DIR/curl"

git -C "$TMP_DIR" init -q
git -C "$TMP_DIR" config core.autocrlf false
git -C "$TMP_DIR" config user.email "test@example.com"
git -C "$TMP_DIR" config user.name "Test User"
cp "$ROOT/.husky/prepare-commit-msg" "$TMP_DIR/prepare-commit-msg"

printf '%s\n' "hello" > "$TMP_DIR/example.txt"
git -C "$TMP_DIR" add example.txt

MSG_FILE="$TMP_DIR/COMMIT_EDITMSG"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/prepare-commit-msg" "$2"' bash "$TMP_DIR" "$MSG_FILE"

ACTUAL="$(cat "$MSG_FILE" 2>/dev/null || true)"
EXPECTED="chore(test): 自动生成提交信息"

if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected: $EXPECTED" >&2
  echo "actual: $ACTUAL" >&2
  exit 1
fi

MESSAGE_SOURCE_MSG_FILE="$TMP_DIR/MESSAGE_SOURCE_EDITMSG"
: > "$MESSAGE_SOURCE_MSG_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/prepare-commit-msg" "$2" message' bash "$TMP_DIR" "$MESSAGE_SOURCE_MSG_FILE"

ACTUAL="$(cat "$MESSAGE_SOURCE_MSG_FILE" 2>/dev/null || true)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected message-source generation: $EXPECTED" >&2
  echo "actual message-source generation: $ACTUAL" >&2
  exit 1
fi

USER_MSG_FILE="$TMP_DIR/USER_EDITMSG"
printf '%s\n' "fix(core): 用户手写提交信息" > "$USER_MSG_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/prepare-commit-msg" "$2" message' bash "$TMP_DIR" "$USER_MSG_FILE"

ACTUAL="$(cat "$USER_MSG_FILE" 2>/dev/null || true)"
EXPECTED_USER_MSG="fix(core): 用户手写提交信息"
if [ "$ACTUAL" != "$EXPECTED_USER_MSG" ]; then
  echo "expected existing message to be preserved: $EXPECTED_USER_MSG" >&2
  echo "actual existing message: $ACTUAL" >&2
  exit 1
fi
