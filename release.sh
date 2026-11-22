#!/usr/bin/env bash
# Публикация новой версии «Заметок-календаря»: сборка, version.json, GitHub release.
# Использование: ./release.sh "Что нового (changelog)"
# Нужно: версия поднята в app/build.gradle.kts (versionCode/versionName), `gh auth login` выполнен.
set -euo pipefail
cd "$(dirname "$0")"
REPO="BlockRoes/zametki-kalendar"
export JAVA_HOME="${JAVA_HOME:-/home/box/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/box/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
CHANGELOG="${1:-Исправления и улучшения}"

CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
APK="Zametki-kalendar-$NAME.apk"
TAG="v$NAME"

./gradlew assembleRelease
cp -f app/build/outputs/apk/release/app-release.apk "$APK"
apksigner verify --print-certs "$APK" | grep "SHA-256"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)

python3 - "$CODE" "$NAME" "$REPO" "$TAG" "$APK" "$SHA" "$CHANGELOG" << 'PY'
import json, sys
code, name, repo, tag, apk, sha, log = sys.argv[1:8]
json.dump({
    "versionCode": int(code), "versionName": name,
    "apkUrl": f"https://github.com/{repo}/releases/download/{tag}/{apk}",
    "changelog": log, "sha256": sha,
}, open("version.json", "w"), ensure_ascii=False, indent=2)
PY

# защита от утечки секретов
if git ls-files | grep -Eq '\.jks$|keystore\.properties|local\.properties|\.apk$'; then
  echo "ОШИБКА: в git попали секреты/APK" >&2; exit 1
fi
git add -A
git diff --cached --quiet || git commit -m "Release $NAME"

# remote без токена в URL; доступ через GH_TOKEN / gh credential helper
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$REPO.git"
else
  git remote add origin "https://github.com/$REPO.git"
fi
REMOTE_URL=$(git remote get-url origin)
case "$REMOTE_URL" in
  *ghp_*|*github_pat_*|*@*) echo "ОШИБКА: в URL remote есть учётные данные: проверьте origin" >&2; exit 1 ;;
esac

git tag -f "$TAG"
git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push -u origin HEAD "refs/tags/$TAG"

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK" version.json --repo "$REPO" --clobber
  gh release edit "$TAG" --repo "$REPO" --title "Заметки-календарь $NAME" --notes "$CHANGELOG"
else
  gh release create "$TAG" "$APK" version.json --repo "$REPO" --title "Заметки-календарь $NAME" --notes "$CHANGELOG"
fi
echo "Готово: https://github.com/$REPO/releases/tag/$TAG"
