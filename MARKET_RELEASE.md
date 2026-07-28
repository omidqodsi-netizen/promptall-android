# promptAll 3.1.0 — Market release

This project builds a signed APK and AAB for Cafe Bazaar.

## One-time GitHub signing setup

Open the repository, then go to:

`Settings > Secrets and variables > Actions > New repository secret`

Create these four repository secrets using the values in the private signing
backup delivered separately:

- `PROMPTALL_KEYSTORE_BASE64`
- `PROMPTALL_STORE_PASSWORD`
- `PROMPTALL_KEY_ALIAS`
- `PROMPTALL_KEY_PASSWORD`

Never commit the keystore, passwords, or Base64 value to the repository.

## Build

Every push to `main`, or a manual run of the workflow, creates:

- `promptAll-v3.1.0-bazaar.apk`
- `promptAll-v3.1.0-bazaar.aab`
- `mapping-v3.1.0.txt`

Download them from the successful GitHub Actions run under
`promptAll-v3.1.0-bazaar-release`.

## Permanent signing key

Keep `promptall-release.p12` and `release-signing-credentials.txt` backed up in
at least two secure locations. Every future update of the same Cafe Bazaar app
must be signed with this exact key.
