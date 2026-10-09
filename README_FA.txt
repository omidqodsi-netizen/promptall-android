PromptAll Android v3.10.1 - Build Hotfix

علت خطای Action قبلی:
فایل apply_patch.py داخل ریپو آپلود شده بود اما Workflow آن را اجرا نمی‌کرد.
در نتیجه dependency رسمی Poolakey، JitPack، aiImageApi، permission بازار و FileProvider
به سورس زمان Build اضافه نشده بودند و Kotlin همه کلاس‌های cafebazaar را Unresolved می‌دید.

این بسته دو کار می‌کند:
1) Workflow ابتدا apply_patch.py نسخه 3.10.0 را اجرا می‌کند.
2) سپس apply_patch_hotfix.py ایراد cameraUri Kotlin را اصلاح و نسخه را 3.10.1 می‌کند.

روش نصب:
- محتویات ZIP را در ریشه repository آپلود و Replace کنید.
- ساختار پوشه .github/workflows را حفظ کنید.
- فایل apply_patch.py قبلی باید همچنان در ریشه repository باقی بماند.
- Commit کنید؛ GitHub Actions خودکار اجرا می‌شود.

خروجی موفق:
promptAll-v3.10.1-bazaar.apk
promptAll-v3.10.1-bazaar.aab
mapping-v3.10.1.txt
