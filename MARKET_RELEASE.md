# PromptAll 3.8.0 — Market release

این پروژه نسخه کامل Android برای انتشار PromptAll 3.8.0 است و با همان applicationId و کلید امضای نسخه منتشرشده در بازار ساخته می‌شود.

## نسخه

- `versionName = 3.8.0`
- `versionCode = 30800`
- `applicationId = ir.promptall.app`

## قابلیت‌های اصلی این نسخه

- جستجوی پرامپت با عکس داخل UI نیتیو اپ
- انتخاب عکس از گالری و Share مستقیم عکس از برنامه‌های دیگر به PromptAll
- جستجوی عادی Privacy-first با pHash / dHash / aHash / histogram و ML Kit
- نمایش نتایج جستجوی معمولی بدون ارسال فایل خام عکس به PromptAll
- کارت واضح «بررسی با هوش مصنوعی» حتی اگر جستجوی عادی نتیجه داشته باشد
- اتصال مستقیم گوشی کاربر به Gemini تا مسیر شبکه/VPN خود کاربر استفاده شود
- fallback خودکار بین چند مدل Gemini اگر مدل اول جواب ندهد
- بررسی دوباره کاندیدهای AI با خود تصویر برای کاهش نتیجه‌های بی‌ربط
- در صورت نبود نتیجه مطمئن: ساخت پرامپت فارسی و انگلیسی همان تصویر
- نمایش خودکار پرامپت ساخته‌شده در همان پنل AI و امکان Copy
- حفظ Categories، Trending، Video Prompts، Favorites، Prompt Detail، Similar Prompts و Open in AI

## افزونه مورد نیاز سایت

PromptAll Image Search `1.3.5` یا جدیدتر باید روی سایت فعال باشد تا تنظیمات AI برای Android از endpoint وضعیت دریافت شود.

## GitHub Actions

Workflow موجود پروژه خروجی‌های نسخه‌ای می‌سازد:

- `promptAll-v3.8.0-release.apk`
- `promptAll-v3.8.0-release.aab`
- `mapping-v3.8.0.txt`

برای اینکه APK/AAB بتواند نسخه فعلی بازار را Update کند، همان Secrets و کلید امضای نسخه منتشرشده قبلی باید استفاده شوند:

- `PROMPTALL_KEYSTORE_BASE64`
- `PROMPTALL_STORE_PASSWORD`
- `PROMPTALL_KEY_ALIAS`
- `PROMPTALL_KEY_PASSWORD`
