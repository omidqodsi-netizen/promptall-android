# Full Checkpoint v3.10.4

پایه این بسته همان Full Source v3.10.3 است و هیچ فایل سورس قبلی حذف نشده است. تغییرات v3.10.4 شامل بازیابی timeout، چند Pending Purchase، دکمه ساخت روی کارت‌ها و اسکرول داخلی متن پرامپت است.

# PromptAll Android v3.10.3 — FULL CHECKPOINT

این بسته یک Patch نیست و کل سورس لازم برای Build اپلیکیشن را در خود دارد.

## روش آپلود در GitHub
محتویات داخل این پوشه/ZIP را در ریشه ریپوی `promptall-android` آپلود و فایل‌های هم‌نام را Replace کنید.
پوشه‌های اصلی `.github` و `app` و فایل‌های Gradle باید مستقیماً در ریشه ریپو قرار بگیرند؛ خود پوشه wrapper اضافی را آپلود نکنید.

## نسخه
- versionName: 3.10.3
- versionCode: 31003
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
