from pathlib import Path

ROOT = Path(__file__).resolve().parent


def patch(path: str, old: str, new: str, count: int = 1):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Hotfix pattern not found in {path}: {old[:180]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


# Bump the hotfix version after the v3.10.0 patch has been applied.
patch(
    "app/build.gradle.kts",
    '        versionCode = 31000\n        versionName = "3.10.0"',
    '        versionCode = 31001\n        versionName = "3.10.1"',
)

# Kotlin cannot smart-cast a delegated mutableStateOf Uri property. Keep the
# freshly-created FileProvider Uri in a local immutable value before launching
# the camera contract.
patch(
    "app/src/main/java/ir/promptall/app/ai/AiImageScreens.kt",
    '''                                cameraUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                cameraLauncher.launch(cameraUri)
''',
    '''                                val captureUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                cameraUri = captureUri
                                cameraLauncher.launch(captureUri)
''',
)

# Guardrails: fail early with an actionable message if the base v3.10 patch was
# not applied. These are exactly the pieces that were missing in the failed run.
required = {
    "settings.gradle.kts": 'maven("https://jitpack.io")',
    "app/build.gradle.kts": 'com.github.cafebazaar.Poolakey:poolakey:2.2.0',
    "app/src/main/java/ir/promptall/app/PromptAllApplication.kt": 'val aiImageApi: AiImageApi',
    "app/src/main/AndroidManifest.xml": 'com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR',
}
for path, needle in required.items():
    text = (ROOT / path).read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(
            f"Required v3.10 patch is missing in {path}: {needle}. "
            "Make sure the workflow runs python3 apply_patch.py before this hotfix."
        )

print("PromptAll Android v3.10.1 build hotfix applied successfully.")
