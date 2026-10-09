# Full Checkpoint v3.10.8

این بسته ادامه مستقیم v3.10.7 است و تمام اصلاحات خرید، بازیابی بازار، session ساخت، ذخیره تصویر و Kill-switch نسخه‌های قبل را حفظ می‌کند.

## وضعیت فعلی
- Android versionName: 3.10.8
- Android versionCode: 31008
- Plugin companion: PromptAll AI Image Generator v1.0.30

## تغییرات این checkpoint
- حفظ کامل اصلاحات v3.10.7: جداسازی نتیجه هر پرامپت، ذخیره گالری، حذف Share و Kill-switch افزونه.
- رفع timeout کوتاه درخواست Generate در Android؛ فقط مسیر ساخت timeout بلند دارد و سایر APIها سریع باقی می‌مانند.
- افزایش reconciliation نتیجه از چند ثانیه به چند دقیقه برای ساخت‌های واقعاً طولانی.
- تبدیل HTTP 409 مربوط به generation-in-progress به پیگیری وضعیت سرور به‌جای خطای نهایی.
- اتصال اتمیک purchase در حال ساخت به generation_id واقعی در افزونه v1.0.30.
- self-heal کردن قفل‌های generating رهاشده و بازگرداندن اعتبار به حالت retryable بدون پرداخت مجدد.
- reconcile وضعیت قبل از جایگزینی پرامپت تا فقط ساخت واقعاً فعال مانع جایگزینی شود.

## ترتیب نصب
1. افزونه v1.0.30 را روی سایت نصب/آپدیت کن.
2. APK را از سورس Android v3.10.8 بیلد کن.
3. چک‌لیست TEST_CHECKLIST_V3.10.8_FA.md را اجرا کن.
