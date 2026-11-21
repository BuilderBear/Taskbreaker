---
name: cut-release
description: Cut a new Taskbreaker release on GitHub. Bumps the Android app version, builds the APK, tags the commit, publishes a GitHub release with the APK attached, and always ends by giving the owner the link to the new release page. Use when the owner asks to "cut a release", "make a release", "publish vX.Y", "release a new version" or similar.
---

# Cut a Taskbreaker release

Publishes a GitHub release of the Android app (repo `BuilderBear/Taskbreaker`) with the installable `.apk` attached.

**The one rule that must never be skipped: the final message always includes the full URL of the new release page**, read from GitHub (not guessed), e.g. `https://github.com/BuilderBear/Taskbreaker/releases/tag/v0.3`.

The repo is **public**, so a release is visible to anyone. The owner asking for a release is the go-ahead to publish it.

All commands are run from the repo root, `C:\Users\natha\Documents\Projects\Launchpad`, unless noted. Build commands need Android Studio's Java:
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (PowerShell).

## Steps

### 1. Check the starting point
- `git status -sb`: the working tree must be clean and `main` must be in sync with `origin/main`. If there are uncommitted changes, stop and ask the owner whether to commit them first. Never include unreviewed changes in a release silently.
- `gh release list --repo BuilderBear/Taskbreaker` and `git tag`: find the latest version.

### 2. Pick the version
- If the owner named a version, use it. Otherwise bump the minor number (v0.2 → v0.3) and say which one you picked.
- The tag must not already exist.
- List what changed since the last tag (`git log --oneline <last-tag>..HEAD`). If nothing changed, tell the owner and stop.

### 3. Bump the app version
In `android/app/build.gradle.kts`, `defaultConfig`:
- `versionName` = the new version without the "v" (e.g. `"0.3"`).
- `versionCode` = previous value + 1. **It must go up**, or phones refuse to install the update over the old one.

### 4. Build and verify the APK
From `android\` (PowerShell):
```
cmd /c ".\gradlew.bat assembleDebug --console=plain"
```
- The build must succeed. If it fails, fix it or report it. Don't publish.
- Confirm the APK reports the new version:
  `& (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt2.exe" | Sort-Object FullName | Select-Object -Last 1).FullName dump badging app\build\outputs\apk\debug\app-debug.apk`
  The first line must show the new `versionCode` and `versionName`.
- If the emulator is available and the release includes UI changes, install it (`.\gradlew.bat installDebug`), open the app, and screenshot to check it starts without crashing (`adb logcat -d -b crash` should be empty).

### 5. Commit, push and tag
```
git add android/app/build.gradle.kts
git commit -m "Bump app version to X.Y"     # plus the attribution trailer
git push origin main
git tag -a vX.Y -m "Taskbreaker vX.Y" HEAD
git push origin vX.Y
```

### 6. Publish the release
- Copy the APK to the scratchpad as `taskbreaker-vX.Y-debug.apk` (a clear name for the download).
- Write release notes in plain language for a non-developer:
  - **What's new**, grouped by feature, from the commits since the last tag.
  - **Install**: download the `.apk` on the phone, open it, allow installing unknown apps if asked. Mention that it installs over the previous version and keeps saved data. That's true while every release is built on this PC with the same debug key.
  - **Status**: it's a debug (test) build, plus whether it was tested on a real phone or only the emulator. Requires Android 8.0+.
- Publish as a **pre-release** while builds are debug builds:
  ```
  gh release create vX.Y <apk path> --repo BuilderBear/Taskbreaker --title "Taskbreaker vX.Y" --notes-file <notes path> --prerelease --verify-tag
  ```

### 7. Get the link and report it
- Read the real URL back from GitHub:
  `gh release view vX.Y --repo BuilderBear/Taskbreaker --json url,isPrerelease,assets`
- Confirm the APK is listed in `assets`.
- **Final message to the owner, starting with the release link**, then: version and commit, what's in it, whether it's a pre-release, how to install on the phone, and anything not tested.

## Notes
- Windows: when running `.bat` tools from PowerShell, arguments containing `;` get split. Use `cmd /c "..."`.
- In PowerShell, pass negative numbers to functions in parentheses (`Pt (-90)`), or they're read as text.
- Don't delete or overwrite an existing release or tag. If something went wrong, ask the owner first.
