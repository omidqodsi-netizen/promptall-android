# آپلود PromptAll Android 3.8.0 روی GitHub

1. فایل ZIP کامل سورس را از ChatGPT دانلود و روی کامپیوتر Extract کنید.
2. وارد ریپوی زیر شوید:
   `omidqodsi-netizen/promptall-android`
3. در شاخه `main` فایل‌های پروژه نسخه قبلی را با محتویات پوشه Extract‌شده جایگزین کنید.
   - فایل ZIP را به‌عنوان یک فایل داخل ریپو آپلود نکنید؛ محتویات پروژه باید در ریشه ریپو باشند.
   - پوشه‌های `app` و `.github` و فایل‌های `build.gradle.kts`، `settings.gradle.kts` و ... باید مستقیماً در ریشه ریپو دیده شوند.
4. Commit را ثبت کنید؛ مثلاً:
   `Release PromptAll Android 3.8.0`
5. وارد تب `Actions` شوید.
6. Workflow با نام `Build promptAll Market Release` را اجرا کنید (`Run workflow`).
7. بعد از سبز شدن Build، از بخش Artifacts فایل `promptAll-v3.8.0-release` را دانلود کنید.
8. داخل Artifact این فایل‌ها قرار دارند:
   - `promptAll-v3.8.0-release.apk`
   - `promptAll-v3.8.0-release.aab`
   - `mapping-v3.8.0.txt`
9. APK/AAB را با همان مسیر قبلی برای آپدیت بازار استفاده کنید.

## قبل از انتشار

- افزونه PromptAll Image Search 1.3.5+ روی سایت فعال باشد.
- API Gemini و گزینه جستجوی AI در تنظیمات افزونه فعال باشد.
- روی یک گوشی واقعی تست کنید: جستجوی عکس، جستجوی AI با VPN، ساخت پرامپت، باز شدن جزئیات، Favorites و Share.
