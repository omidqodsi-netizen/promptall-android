# PromptAll Android v3.10.7

نسخه پایه: v3.10.6
versionCode: 31007
افزونه همراه: PromptAll AI Image Generator v1.0.29

## اصلاحات اصلی

1. رفع نمایش نتیجه/پرامپت ساخت قبلی هنگام ورود به ساخت یک پرامپت جدید.
   - نتیجه ساخت اکنون با postId همان پرامپت scope می‌شود.
   - با ورود به هر Generator Session، نتیجه UI قبلی پاک می‌شود.
   - اگر یک پاسخ قدیمی بعداً از سرور برسد، فقط برای همان پرامپت قابل نمایش است و روی پرامپت جدید نشت نمی‌کند.

2. ذخیره واقعی تصویر در گالری.
   - DownloadManager ساده حذف شد.
   - تصویر با OkHttp دریافت و در Android 10+ با MediaStore در Pictures/PromptAll ذخیره می‌شود.
   - Android 8/9 پس از گرفتن WRITE_EXTERNAL_STORAGE در Pictures/PromptAll ذخیره و MediaScanner اجرا می‌شود.
   - پیام موفق/ناموفق به کاربر نمایش داده می‌شود.

3. حذف دکمه اشتراک تصاویر ساخته‌شده.
   - از کارت نتیجه ساخت حذف شد.
   - از پیش‌نمایش تصاویر بخش «تصاویر من» نیز حذف شد.

4. Kill-switch قابل کنترل از افزونه.
   - قبل از هر لمس دکمه ساخت، وضعیت قابلیت از سرور با cache-bust تازه‌خوانی می‌شود.
   - اگر مدیر قابلیت را خاموش کرده باشد Generator باز نمی‌شود و پیام «متأسفانه فعلاً این قابلیت در دسترس نیست.» نمایش داده می‌شود.

## فایل‌های اصلی تغییرکرده
- app/src/main/java/ir/promptall/app/MainActivity.kt
- app/src/main/java/ir/promptall/app/ai/AiImageScreens.kt
- app/src/main/java/ir/promptall/app/ai/AiImageViewModel.kt
- app/src/main/java/ir/promptall/app/data/remote/AiImageApi.kt
- app/build.gradle.kts
