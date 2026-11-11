#!/usr/bin/env bash
#
# Builds the fork's arm64-v8a release and debug APKs from a committed git ref (normally a release
# tag), using F-Droid's recipe for this app: the nonfree (Firebase) code is removed. See FORK.md.
#
# Usage:
#   fork/build-release.sh <tag> [ref]          Build into $OUT_DIR/<tag>/ (ref defaults to <tag>)
#   fork/build-release.sh --prepare <dir> <ref>  Only export and patch the source into <dir>
#
# Environment:
#   SIGNING_ENV  File exporting STORE_FILE, STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD for the
#                release key (default: ~/.android/materialfiles-release/signing.env). If those
#                variables are already set, the file is not needed.
#   OUT_DIR      Output directory (default: build/fork-releases in this repo, which is gitignored).

set -euo pipefail

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
SIGNING_ENV=${SIGNING_ENV:-$HOME/.android/materialfiles-release/signing.env}
OUT_DIR=${OUT_DIR:-$REPO/build/fork-releases}

# In-place sed that works with both GNU and BSD (macOS) sed.
if sed --version >/dev/null 2>&1; then sed_i() { sed -i "$@"; }; else sed_i() { sed -i '' "$@"; }; fi

prepare() {
    local src=$1 ref=$2
    mkdir -p "$src"
    git -C "$REPO" archive "$ref" | tar -x -C "$src"
    cd "$src/app"
    # F-Droid's recipe (fdroiddata: metadata/me.zhanghai.android.files.yml).
    rm -r src/main/java/me/zhanghai/android/files/nonfree
    grep -rl '//#ifdef NONFREE' . | while read -r file; do
        sed_i '/\/\/#ifdef NONFREE/,/\/\/#endif/d' "$file"
    done
    if grep -rq 'NONFREE' .; then
        echo "NONFREE markers left over" >&2
        exit 1
    fi
    # arm64-v8a only: add ndk.abiFilters before the externalNativeBuild block in defaultConfig.
    sed_i "s/^\(        externalNativeBuild {\)$/        ndk {\\
            abiFilters 'arm64-v8a'\\
        }\\
\1/" build.gradle
    if [ "$(grep -c "abiFilters 'arm64-v8a'" build.gradle)" != 1 ]; then
        echo "abiFilters not added exactly once; check the layout of app/build.gradle" >&2
        exit 1
    fi
}

if [ "${1:-}" = --prepare ]; then
    prepare "${2:?--prepare needs <dir> <ref>}" "${3:?--prepare needs <dir> <ref>}"
    exit 0
fi

TAG=${1:?usage: fork/build-release.sh <tag> [ref]}
REF=${2:-$TAG}
OUT="$OUT_DIR/$TAG"
if [ -e "$OUT" ]; then
    echo "$OUT already exists" >&2
    exit 1
fi
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"

if [ -z "${STORE_FILE:-}" ]; then
    # shellcheck source=/dev/null
    source "$SIGNING_ENV"
fi

SRC=$(mktemp -d "${TMPDIR:-/tmp}/materialfiles-release.XXXXXX")
trap 'rm -rf "$SRC"' EXIT
echo "Preparing $REF ($(git -C "$REPO" rev-parse --short "$REF^{commit}")) in $SRC"
prepare "$SRC" "$REF"

cd "$SRC"
./gradlew --console=plain assembleRelease assembleDebug

mkdir -p "$OUT"
for type in release debug; do
    apk="$OUT/MaterialFiles-$TAG-arm64-v8a-$type.apk"
    cp "app/build/outputs/apk/$type/app-$type.apk" "$apk"
    echo "== $(basename "$apk") ($(du -h "$apk" | cut -f 1))"
    "$BUILD_TOOLS/apksigner" verify --print-certs "$apk" | grep -E 'DN|certificate SHA-256'
    abis=$(zipinfo -1 "$apk" | grep '^lib/' | cut -d / -f 2 | sort -u | tr '\n' ' ')
    echo "   Native ABIs: $abis"
    if [ "$abis" != "arm64-v8a " ]; then
        echo "Unexpected native ABIs in $apk" >&2
        exit 1
    fi
done
cp app/build/outputs/mapping/release/mapping.txt "$OUT/mapping-release.txt"
(cd "$OUT" && shasum -a 256 ./*.apk | sed 's| \./| |' > SHA256SUMS && cat SHA256SUMS)
echo "Done: $OUT"
