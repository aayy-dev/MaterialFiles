# About this fork

This is [Material Files](https://github.com/zhanghai/MaterialFiles) with three changes that aren't upstream:

- **SMB login fix for macOS servers.** SMBJ 0.11.5 computes the NTLM MIC incorrectly, and macOS rejects it. Only SMBJ's NTLM authenticator is replaced, so the app stays on SMBJ 0.11.5 and avoids the anonymous-login regression in newer versions. Proposed upstream in [#1672](https://github.com/zhanghai/MaterialFiles/pull/1672).
- **DocumentsProvider.** Material Files' storages and servers appear in the system file picker. Other apps can pick folders as well as files. With DocumentsUI disabled, Material Files handles all picking. Root and Shizuku locations need the user's consent. Proposed upstream in [#1673](https://github.com/zhanghai/MaterialFiles/pull/1673).
- **Open with chooser.** Tapping a file opens the app's own chooser instead of Android's, with "Always" for the file or for its extension. These defaults are kept in the app and can be cleared from the chooser or from Settings. It fixes unknown file types (opened as any type) being captured by an unrelated system "Always" choice. This one is fork-only: the maintainer considers default apps a system matter ([#1166](https://github.com/zhanghai/MaterialFiles/issues/1166)).

Releases are on the [Releases page](https://github.com/aayy-dev/MaterialFiles/releases). Their package name is the same as upstream, but their signing key isn't, so they can't update an upstream install.

## Branches

| Branch | Contents |
|---|---|
| `main` (default) | `upstream` plus this fork's commits. Releases are tagged here. |
| `upstream` | An unmodified mirror of zhanghai/MaterialFiles `master`. |
| `pr-smb`, `pr-docsui` | The branches behind upstream PRs #1672 and #1673. Keep them in upstream's style, without fork-only files. |

Only the fork's own files (`FORK.md`, `fork/`) and the commits for the changes above sit on top of `upstream`.

## Syncing with upstream

```sh
git remote add zhanghai https://github.com/zhanghai/MaterialFiles.git   # once
git fetch zhanghai
git switch upstream && git merge --ff-only zhanghai/master && git push origin upstream
git switch main && git rebase upstream    # resolve any conflicts, then build and test
git push --force-with-lease origin main
```

Rebasing rewrites the fork's commits. Old release tags still point to the commits they were built from.

If upstream merges one of the PRs, drop the matching commits from `main` during the rebase, and delete its `pr-*` branch.

## Releasing

Tags are named `v<upstream versionName>.<n>`, for example `v1.7.5.2`. Start `<n>` at 1 for each new upstream version, and increase it for each rebuild on the same upstream version. The first release, `v1.7.5-docsui-smb.1`, predates this scheme, so 1.7.5 continues at `.2`.

The build script rejects tags that don't match upstream's versionName. It sets the APKs' versionName to the tag's version (e.g. 1.7.5.2), so About shows which build is installed. The version code stays upstream's.

```sh
TAG=v1.7.5.2
git tag -a "$TAG" -m "Material Files 1.7.5 + this fork's changes" main
fork/build-release.sh "$TAG"           # writes build/fork-releases/$TAG/
# Install the release APK on a device and test it before publishing.
git push origin "$TAG"
gh release create "$TAG" --verify-tag --title "..." --notes-file notes.md \
    build/fork-releases/"$TAG"/*.apk build/fork-releases/"$TAG"/SHA256SUMS
```

`fork/build-release.sh` builds from the committed tag, not the working tree. It:

1. Checks the tag and sets the versionName from it.
2. Applies [F-Droid's recipe for this app](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/me.zhanghai.android.files.yml): it deletes `app/src/main/java/me/zhanghai/android/files/nonfree` and every `//#ifdef NONFREE` … `//#endif` block, so the builds contain no Firebase.
3. Limits native code to arm64-v8a.
4. Builds the release and debug APKs.
5. Checks their signatures, ABIs and versionName, writes `SHA256SUMS`, and keeps R8's `mapping-release.txt` for decoding stack traces from release builds.

## Signing

- **Release APKs** are signed with this fork's own key, which isn't in the repo. The build script reads it from `~/.android/materialfiles-release/signing.env`, or from `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` if they're already set.
  - **Every release must use the same key**, or existing installs can't update. Keep an offline backup of the keystore and its password.
  - Certificate SHA-256: `25:D0:1E:5A:D7:00:8F:F6:FB:42:AB:06:21:75:6E:B3:D5:EA:42:4E:9E:75:C0:9A:A9:58:91:D4:14:4E:E9:36`
- **Debug APKs** are signed with the building machine's Android debug key. They can't update release installs, or the other way round.
