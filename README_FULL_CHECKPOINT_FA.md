# PromptAll Android v3.10.2 — FULL CHECKPOINT

این بسته یک Patch نیست و کل سورس لازم برای Build اپلیکیشن را در خود دارد.

## روش آپلود در GitHub
محتویات داخل این پوشه/ZIP را در ریشه ریپوی `promptall-android` آپلود و فایل‌های هم‌نام را Replace کنید.
پوشه‌های اصلی `.github` و `app` و فایل‌های Gradle باید مستقیماً در ریشه ریپو قرار بگیرند؛ خود پوشه wrapper اضافی را آپلود نکنید.

## نسخه
- versionName: 3.10.2
- versionCode: 31002
- Package: ir.promptall.app

## قابلیت جدید
- ساخت تصویر از صفحه پرامپت با عکس مرجع
- پرداخت درون‌برنامه‌ای کافه‌بازار با Poolakey
- قیمت و SKU دریافتی از سرور PromptAll
- Verify سروری خرید
- بازیابی خرید نیمه‌تمام و Retry
- پروفایل/گالری تصاویر ساخته‌شده
- انتخاب عکس از گالری و دوربین

## GitHub Actions
Workflow داخل `.github/workflows/build-apk.yml` مستقیماً همین سورس کامل را Build می‌کند و دیگر هیچ Patch runtime اجرا نمی‌شود.
Secrets امضای Release قبلی باید مثل قبل در GitHub باقی مانده باشند.
