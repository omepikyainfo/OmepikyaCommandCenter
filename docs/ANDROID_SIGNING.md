# Android debug signing setup

The repository is public. A previous debug keystore was committed to source control, so treat that key as compromised. Removing the file from the branch does not remove it from Git history.

## What CI does now

- The debug build uses a repeatable key only when all four GitHub Actions secrets are configured.
- Without those secrets, Gradle uses its standard debug signing key.
- Release builds are not configured for production signing. Do not distribute the release artifact as a stable, updateable personal install.

## Configure secrets from a phone

In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add these four secrets:

- `OMEPIKYA_DEBUG_KEYSTORE_B64`: Base64 text for a newly generated debug keystore.
- `OMEPIKYA_DEBUG_STORE_PASSWORD`: that keystore's store password.
- `OMEPIKYA_DEBUG_KEY_ALIAS`: alias for the key.
- `OMEPIKYA_DEBUG_KEY_PASSWORD`: password for that key.

Generate a fresh keystore in a trusted environment; do not reuse the exposed key and do not paste any key or password into chat or source files. Keep an encrypted backup of the new keystore outside GitHub. Android's `keytool` and a Base64 encoder are needed to create the secret value.

If an APK signed with the exposed key is already installed, the first install signed with the new key cannot update it in place. Back up any app data you need, then uninstall the old app and install the new APK. Standard debug signing may also vary between build environments, so configure the secrets before relying on in-place CI updates.

## Validation

After configuring the secrets, run the Android CI workflow and install the uploaded **debug** APK artifact. Keep production release signing separate and private.
