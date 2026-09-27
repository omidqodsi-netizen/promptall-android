# PromptAll 3.9.0 — Market release

این پروژه نسخه کامل Android برای انتشار PromptAll 3.9.0 است و با همان applicationId و کلید امضای نسخه منتشرشده در بازار ساخته می‌شود.

## نسخه

- `versionName = 3.9.0`
- `versionCode = 30900`
- `applicationId = ir.promptall.app`

## قابلیت‌های اصلی این نسخه

- جستجوی پرامپت با عکس داخل UI نیتیو اپ
- انتخاب عکس از گالری و Share مستقیم عکس از برنامه‌های دیگر به PromptAll
- جستجوی عادی Privacy-first با pHash / dHash / aHash / histogram؛ بدون ارسال یا ذخیره فایل خام عکس
- حفظ نسخه Lite بدون ML Kit سنگین و بدون dependency جدید حجیم
- اتصال مستقیم گوشی کاربر به Gemini با مسیر شبکه/VPN خود کاربر
- fallback خودکار بین چند مدل Gemini و ساخت پرامپت فارسی/انگلیسی در صورت نبود نتیجه مطمئن
- UI/UX جدید برای Home، Search، کارت‌ها، دسته‌بندی‌ها و Bottom Navigation
- نمایش سهمیه باقی‌مانده و پیام دوستانه هنگام پایان سهمیه روزانه
- حفظ Categories، Trending، Video Prompts، Favorites، Prompt Detail، Similar Prompts و Open in AI

## افزونه مورد نیاز سایت

PromptAll Image Search `1.3.5` یا جدیدتر باید روی سایت فعال باشد تا تنظیمات AI برای Android از endpoint وضعیت دریافت شود.

## GitHub Actions

Workflow نام خروجی را مستقیماً از `versionName` می‌خواند و خروجی‌های زیر را می‌سازد:

- `promptAll-v3.9.0-release.apk`
- `promptAll-v3.9.0-release.aab`
- `mapping-v3.9.0.txt`

برای اینکه APK/AAB بتواند نسخه فعلی بازار را Update کند، همان Secrets و کلید امضای نسخه منتشرشده قبلی باید استفاده شوند:

- `PROMPTALL_KEYSTORE_BASE64`
- `PROMPTALL_STORE_PASSWORD`
- `PROMPTALL_KEY_ALIAS`
- `PROMPTALL_KEY_PASSWORD`
