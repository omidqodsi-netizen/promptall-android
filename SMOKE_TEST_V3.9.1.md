# Smoke Test — PromptAll Android v3.9.1

پس از Build این موارد بررسی شوند:

1. اپ با Home > تب «تصویری» باز شود.
2. نمای پیش‌فرض Home برای نصب/آپدیت بدون preference قبلی، Grid جورچینی دو ستونه باشد.
3. تب «ویدئو» فقط دسته ویدئویی را فراخوانی کند و برگشت به «تصویری» دسته visual-prompts را فراخوانی کند.
4. دکمه Image Search در هدر Home صفحه جستجو را مستقیماً در حالت تصویر باز کند.
5. دکمه Search عادی صفحه جستجو را در حالت متن باز کند.
6. Bottom Navigation روی محتوا نیفتد؛ Refresh از قاب بیرون زده و قابل لمس باشد.
7. Copy روی کارت Grid و List واضح و بدون باز کردن Detail کار کند.
8. باز کردن Prompt Detail: اگر `aiprompt` یا `ai_model` در API موجود باشد، نام ChatGPT/Gemini/Grok/Sora نشان داده شود.
9. اگر مدل AI موجود نبود، Badge دسته‌بندی نشان داده شود و با لمس آن همان دسته باز شود.
10. Image Search معمولی، AI fallback، سهمیه روزانه و Generated Prompt بدون Regression کار کنند.
11. APK Release همچنان R8/Shrink فعال و فاقد ML Kit سنگین باشد.
