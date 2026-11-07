# VR Hub in-app update system

The app has a manual **Check for Updates** action. It reads `update.json` from the repository's main branch, compares `versionCode`, downloads the APK from the latest GitHub Release, then opens Android's package installer.

## Publishing a new update

1. Build and sign a new APK with the **same signing key** as the installed app.
2. Increase `versionCode` (for example 2) and set a user-facing `versionName` (for example 0.2).
3. Create a GitHub Release and attach the signed APK with the exact asset name `app-release.apk`.
4. Update `update.json` on main with the new version code/name and release notes.
5. Users open VR Hub and tap **Check Updates**.

## Important signing requirement

Android only installs an in-place update when the new APK has the same application ID and is signed by the same certificate as the installed APK. The current GitHub Actions workflow builds an unsigned-by-project-configuration debug APK using the runner's debug key. GitHub-hosted runners may generate a different debug key on later runs. Therefore, do not assume a later APK can update an already-installed APK until a persistent signing key is configured and verified. If the original APK's signing key cannot be recovered, users will need to uninstall the old app and install a newly signed baseline APK once.

## Permissions and safety

Android still displays its package installer confirmation. The app cannot silently install updates. Only publish trusted APKs and keep the signing key private.
