# PromptAll Android v3.10.2 — FULL SOURCE BUILD FIX

این نسخه مستقیماً از سورس کامل و سالم `v3.9.1-ui-ux-full-source` ساخته شده است؛ هیچ فایل پایه‌ای از نسخه 3.9.1 حذف نشده است.

## پایه پروژه
- تعداد فایل‌های نسخه 3.9.1 کاربر: 40 فایل
- تمام 40 فایل پایه در این Checkpoint وجود دارند.
- فایل‌های قابلیت AI/بازار و مستندات تکمیلی به پروژه افزوده شده‌اند.

## تغییرات 3.10.2
- قابلیت ساخت تصویر از صفحه تکی پرامپت با عکس مرجع
- صفحه Native ساخت تصویر با UI/UX هماهنگ با PromptAll
- پروفایل و گالری تصاویر ساخته‌شده
- اتصال پرداخت درون‌برنامه‌ای کافه‌بازار با Poolakey 2.2.0
- دریافت SKU و RSA از سرور PromptAll
- Verify سروری Purchase Token
- بازیابی خرید نیمه‌تمام و Retry بدون پرداخت مجدد
- پشتیبانی انتخاب عکس از گالری و دوربین
- FileProvider امن برای دوربین
- versionName = 3.10.2
- versionCode = 31002

## رفع خطای Build اخیر
آخرین GitHub Action فقط یک خطای Kotlin داشت:

`AiImageScreens.kt: Cannot access RowColumnParentData?.weight`

علت import مستقیم `androidx.compose.foundation.layout.weight` بود. این import حذف شده و استفاده از `Modifier.weight()` داخل Row/Column scope باقی مانده است.

## نکته
این بسته FULL SOURCE است و به `apply_patch.py` یا Hotfix runtime وابسته نیست. Workflow مستقیماً سورس نهایی را Build می‌کند.
