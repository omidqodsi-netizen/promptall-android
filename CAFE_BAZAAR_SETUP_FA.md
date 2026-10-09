# تنظیم کافه‌بازار برای PromptAll AI

## محصول واقعی

- Package: `ir.promptall.app`
- SKU واقعی: `ai_image_single`
- نوع: یک‌بار خرید / محصول فروشی مصرفی
- قیمت فعلی: مطابق پنل بازار (اپ قیمت را از خود بازار می‌خواند)

## محصول تست پیشنهادی

برای تست قبل از انتشار، یک SKU جدا با قیمت صفر ریال بسازید:

- SKU: `ai_image_test`
- عنوان: تست ساخت تصویر
- نوع: یک‌بار خرید
- قیمت: 0 ریال
- وضعیت: فعال

بعد Product ID را موقتاً در تنظیمات افزونه PromptAll AI روی سرور به `ai_image_test` تغییر دهید. چون اپ Product ID را از API سرور دریافت می‌کند، نیازی به Build مجدد نیست.

## RSA

RSA Public Key بازار در تنظیمات افزونه سایت قرار می‌گیرد و از API config به اپ داده می‌شود. RSA کلید عمومی است.

## API Token پیشخان

توکن API پیشخان بازار (`CAFEBAZAAR-PISHKHAN-API-SECRET`) فقط روی سرور PromptAll ذخیره می‌شود. آن را:

- داخل APK قرار ندهید.
- داخل GitHub Secret برای این قابلیت هم نیاز ندارید.
- در فایل Kotlin یا BuildConfig هاردکد نکنید.

سرور با این توکن Purchase Token را مستقیماً در API رسمی بازار اعتبارسنجی می‌کند.

## گردش امن

1. App از سرور Purchase Session می‌گیرد.
2. App محصول را با Poolakey از بازار می‌خرد.
3. App Purchase Token را به سرور PromptAll می‌فرستد.
4. سرور خرید را با بازار Verify می‌کند.
5. App محصول مصرفی را Consume می‌کند.
6. سرور Consumption State را دوباره بررسی می‌کند.
7. یک Generation برای همان Purchase آزاد می‌شود.
8. اگر Generation خطا بخورد، سرور وضعیت `generation_failed` را نگه می‌دارد و Retry بدون خرید جدید ممکن است.
