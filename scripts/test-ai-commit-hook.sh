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
cp "$ROOT/.husky/commit-msg" "$TMP_DIR/commit-msg"
cp "$ROOT/.husky/ai-commit-message" "$TMP_DIR/ai-commit-message"

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

TEMPLATE_MSG_FILE="$TMP_DIR/TEMPLATE_EDITMSG"
printf '%s\n' "__AI_COMMIT_MESSAGE__" > "$TEMPLATE_MSG_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/prepare-commit-msg" "$2" template' bash "$TMP_DIR" "$TEMPLATE_MSG_FILE"

ACTUAL="$(cat "$TEMPLATE_MSG_FILE" 2>/dev/null || true)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected template marker to be replaced: $EXPECTED" >&2
  echo "actual template marker result: $ACTUAL" >&2
  exit 1
fi

MESSAGE_MARKER_MSG_FILE="$TMP_DIR/MESSAGE_MARKER_EDITMSG"
printf '%s\n' "__AI_COMMIT_MESSAGE__" > "$MESSAGE_MARKER_MSG_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/prepare-commit-msg" "$2" message' bash "$TMP_DIR" "$MESSAGE_MARKER_MSG_FILE"

ACTUAL="$(cat "$MESSAGE_MARKER_MSG_FILE" 2>/dev/null || true)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected message marker to be replaced: $EXPECTED" >&2
  echo "actual message marker result: $ACTUAL" >&2
  exit 1
fi

COMMIT_MSG_MARKER_FILE="$TMP_DIR/COMMIT_MSG_MARKER_EDITMSG"
printf '  __AI_COMMIT_MESSAGE__\r\n' > "$COMMIT_MSG_MARKER_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/commit-msg" "$2"' bash "$TMP_DIR" "$COMMIT_MSG_MARKER_FILE"

ACTUAL="$(cat "$COMMIT_MSG_MARKER_FILE" 2>/dev/null || true)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected commit-msg marker fallback: $EXPECTED" >&2
  echo "actual commit-msg marker fallback: $ACTUAL" >&2
  exit 1
fi

COMMIT_MSG_USER_FILE="$TMP_DIR/COMMIT_MSG_USER_EDITMSG"
printf '%s\n' "docs(readme): 用户手动提交说明" > "$COMMIT_MSG_USER_FILE"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  bash -c 'cd "$1" && bash "$1/commit-msg" "$2"' bash "$TMP_DIR" "$COMMIT_MSG_USER_FILE"

ACTUAL="$(cat "$COMMIT_MSG_USER_FILE" 2>/dev/null || true)"
EXPECTED_COMMIT_USER_MSG="docs(readme): 用户手动提交说明"
if [ "$ACTUAL" != "$EXPECTED_COMMIT_USER_MSG" ]; then
  echo "expected commit-msg user message to be preserved: $EXPECTED_COMMIT_USER_MSG" >&2
  echo "actual commit-msg user message: $ACTUAL" >&2
  exit 1
fi

git -C "$TMP_DIR" config core.hooksPath "$TMP_DIR"
PATH="$TMP_DIR:$PATH" DEEPSEEK_API_KEY="test-key" \
  git -C "$TMP_DIR" commit -m "__AI_COMMIT_MESSAGE__" >/dev/null

ACTUAL="$(git -C "$TMP_DIR" log -1 --pretty=%s)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected real git commit message: $EXPECTED" >&2
  echo "actual real git commit message: $ACTUAL" >&2
  exit 1
fi
