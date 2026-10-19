# Android release signing

Android CI can build either a signed release APK or an explicitly named unsigned release APK.

## Configure signing once

1. On a trusted computer, create a release keystore and keep a secure offline backup. **Never commit the keystore or passwords to Git.** Losing the keystore can prevent updates to apps signed with it.
   ```sh
   keytool -genkeypair -v -keystore omepikya-release.jks -alias omepikya -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Encode the keystore as one Base64 line:
   - Linux: `base64 -w 0 omepikya-release.jks`
   - macOS: `base64 < omepikya-release.jks | tr -d '\n'`
   - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("omepikya-release.jks"))`
3. In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `ANDROID_KEYSTORE_BASE64`: the complete Base64 output (not a file path)
   - `ANDROID_KEYSTORE_PASSWORD`: the keystore password
   - `ANDROID_KEY_ALIAS`: the alias, e.g. `omepikya`
   - `ANDROID_KEY_PASSWORD`: the key password
4. Run the Android CI workflow again. With all four secrets configured, Gradle signs the release APK and CI uploads it as `omepikya-command-center-release-signed`. If the keystore secret is absent, CI deliberately produces and labels an unsigned artifact instead.

## Security notes

- Do not paste passwords or Base64 keystore content into issues, pull requests, chat, or source files.
- Restrict repository secret access to trusted maintainers and protect the main branch.
- This workflow does not create a signing key automatically and cannot sign a production APK until the repository owner configures the four secrets.
- A successful build is not a substitute for installing and testing the signed APK on a real device before distribution.
