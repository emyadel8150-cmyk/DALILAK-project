# UPLOAD_STATUS.md — دليلك (Dalilak)

## آخر محاولة: رفع النسخة 1.0.1
- التاريخ والوقت: 2026-11-18 — 04:37 (توقيت القاهرة)
- الريبو: https://github.com/emyadel8150-cmyk/DALILAK-project
- الـ branch: `main`
- الرؤية: **Public**

### الحالة
**Uploaded and verified**

التحقق اللي اتعمل فعلًا:
- `git ls-remote` رجّع نفس الـ commit المحلي لـ `main`: `f7a17af`.
- سحب نسخة جديدة من الريبو: 33 ملف، `versionCode = 2` و`versionName = "1.0.1"`، و`TavilyClient.kt` موجود، والموديل الافتراضي `gemini-3.8-flash`.
- مفيش أي ملف مفتاح أو باسورد في الريبو.

### اللي اترفع في المحاولة دي (12 ملف)
- جديد: `app/src/main/java/com/casper/dalilak/ai/TavilyClient.kt`, `DALILAK_STATE_v1.0.1.md`
- معدّل: `GeminiClient.kt`, `AiModels.kt`, `AppSettings.kt`, `AppViewModel.kt`, `Translations.kt`, `ui/Screens.kt`, `app/build.gradle.kts` (الإصدار بس), `DALILAK_STATE.md`, `DALILAK_TODO.md`, `PROJECT_STATUS.md`

### اللي ماترفعش عمدًا من الـ zip
- `dalilak.jks` و`keystore.properties` (مفتاح التوقيع وباسورداته): مستثنيين بـ `.gitignore`.
- `.gitignore` و`.github/workflows/build.yml` اللي في الـ zip: كانوا **النسخة القديمة** (من غير استثناء المفتاح ومن غير خطوة الـ Secrets)، فسيبت النسختين اللي على الريبو. لو اترفعوا كانوا هيرجّعوا المشكلة ويرفعوا المفتاح على ريبو عام.
- `UPLOAD_STATUS.md` مش في الـ zip، فاتحدّث على الريبو بدل ما يتمسح.

### نتيجة فحص البيانات الحساسة
- الـ zip كان فيه `dalilak.jks` (نفس مفتاح 1.0 بالظبط) و`keystore.properties`: متستثنيين، وتأكدت قبل الـ commit وبعد الرفع.
- مفيش مفاتيح API (Gemini/Tavily) ولا توكنات مكتوبة في الكود. مفتاح Tavily بيتحط من الإعدادات في التطبيق ومتخزّن مشفّر على الموبايل.
- مفيش `local.properties` ولا `.env` ولا `google-services.json` ولا APK/AAB.

### الأوامر اللي اتشغلت فعلًا
```bash
unzip Dalilak_v1.0.1.zip            # في فولدرات منفصلة
git clone https://github.com/emyadel8150-cmyk/DALILAK-project.git
# نسخ ملفات التطبيق والتوثيق المعدّلة فقط (من غير .gitignore و build.yml والمفتاح)
git add -A
git commit -m "Release 1.0.1: Tavily search with Gemini, graceful fallback"
git remote set-url origin https://github.com/emyadel8150-cmyk/dalilak-project   # نفس الريبو
git push origin main
git ls-remote https://github.com/emyadel8150-cmyk/DALILAK-project.git refs/heads/main
git clone --depth 1 https://github.com/emyadel8150-cmyk/DALILAK-project.git      # للتحقق
```

### مشاكل وإيه اللي فاضل
1. الـ workflow بيشتغل لوحده بعد كل push على `main`. ماقدرتش أشوف نتيجته (الـ token بتاع `gh` عندي غير صالح)، فنجاح بناء 1.0.1 مش متأكد منه. حسب ملف الحالة، 1.0.1 ماتبنتش ولا اتجرّبت.
2. **مفتاح Tavily:** لازم يتعمل من app.tavily.com ويتحط في إعدادات التطبيق. وحسب ملف الحالة، endpoint بتاع Tavily مبني على التوثيق وماتجرّبش فعليًا.
3. الريبو **Public**: المفتاح مش فيه، بس الكود ظاهر. لو عايزه Private: Settings ← General ← Danger Zone ← Change visibility.
4. احتفظ بنسخة من `dalilak.jks` و`keystore.properties` بره GitHub. المفتاح ما يتولّدش تاني.
5. الـ 4 Secrets (`DALILAK_KEYSTORE_BASE64`, `DALILAK_STORE_PASSWORD`, `DALILAK_KEY_ALIAS`, `DALILAK_KEY_PASSWORD`) لازم يفضلوا موجودين في إعدادات الريبو عشان البناء يشتغل.

---

## المحاولة السابقة: رفع النسخة 1.0
- التاريخ والوقت: 2026-11-17 — 21:49 (توقيت القاهرة)
- الحالة: **Uploaded and verified** (commit `5962689` ثم `0c420d5`، 31 ملف)
- اللي اتعمل: إضافة استثناء المفتاح والباسوردات في `.gitignore`، وإضافة خطوة "Restore signing key from GitHub Secrets" في `build.yml`.
- الأوامر: `git init -b main` ← `git add -A` ← `git check-ignore -v dalilak.jks keystore.properties` ← `git commit` ← `git remote add origin ...` ← `git push -u origin main` ← `git ls-remote` ← `git clone --depth 1` (للتحقق).
