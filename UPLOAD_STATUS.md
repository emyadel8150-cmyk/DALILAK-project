# UPLOAD_STATUS.md — دليلك (Dalilak)

## تاريخ ووقت المحاولة
2026-11-17 — 21:49 (توقيت القاهرة)

## الريبو والـ branch
- الريبو: https://github.com/emyadel8150-cmyk/DALILAK-project
- الـ branch: `main`
- الرؤية: **Public** (ملف الحالة بيقول إن المقصود Private، فيه تنبيه تحت)

## الحالة
**Uploaded and verified**

التحقق اللي اتعمل فعلًا:
- `git ls-remote` رجّع نفس الـ commit المحلي لـ `main`: `5962689`.
- سحب نسخة جديدة من الريبو: 31 ملف، ومفيش فيها أي ملف مفتاح أو باسورد.
- خطوة الـ Secrets موجودة في `.github/workflows/build.yml` على الريبو.

## الملفات اللي اترفعت (31 ملف)
- `.github/workflows/build.yml`, `.gitignore`
- `DALILAK_STATE.md`, `DALILAK_STATE_v1.0.md`, `DALILAK_TODO.md`, `PROJECT_STATUS.md`, `UPLOAD_STATUS.md`
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/schemas/.gitkeep`, `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/casper/dalilak/` (11 ملف Kotlin)
- `app/src/main/res/` (6 ملفات resources)

## الملفات المستثناة بـ `.gitignore`
- `dalilak.jks` و`keystore.properties` (مفتاح التوقيع وباسورداته)
- أنماط عامة: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `*.jks`, `*.keystore`, `*.apk`, `*.aab`, `*.ap_`, `.cxx/`, `.externalNativeBuild/`, `captures/`, `.env`, `.env.*`, `.DS_Store`, `Thumbs.db`, `*.log`

## نتيجة فحص البيانات الحساسة
- `dalilak.jks` و`keystore.properties` كانوا في المشروع و`.gitignore` القديم ماكانش بيستثنيهم. اتضافوا للاستثناء، وتأكدت قبل الـ commit وبعد الرفع إنهم مش موجودين في الريبو.
- مفيش مفاتيح API ولا توكنات مكتوبة في الكود (سكان على الأنماط المعروفة).
- مفيش `local.properties` ولا `.env` ولا `google-services.json` ولا APK/AAB.

## التعديل الوحيد على غير `.gitignore`
`.github/workflows/build.yml`: اتضافت خطوة "Restore signing key from GitHub Secrets" قبل البناء، بتكتب `dalilak.jks` و`keystore.properties` من الـ Secrets. كود التطبيق و`build.gradle.kts` ماتلمسوش.

## الأوامر اللي اتشغلت فعلًا
```bash
git init -b main
git add -A
git check-ignore -v dalilak.jks keystore.properties
git commit -m "Initial commit: Dalilak v1.0"
git remote add origin https://github.com/emyadel8150-cmyk/dalilak-project   # نفس الريبو (الاسم مش حساس للحروف)
git push -u origin main
git ls-remote https://github.com/emyadel8150-cmyk/DALILAK-project.git refs/heads/main
git clone --depth 1 https://github.com/emyadel8150-cmyk/DALILAK-project.git   # للتحقق
```

## مشاكل وإيه اللي فاضل
1. **لازم تضيف 4 Secrets في الريبو**، وإلا البناء على GitHub Actions هيفشل (أول تشغيل بعد الرفع هيطلع أحمر لحد ما تضيفهم):
   Settings ← Secrets and variables ← Actions ← New repository secret
   - `DALILAK_KEYSTORE_BASE64`: محتوى `dalilak.jks` بصيغة base64
   - `DALILAK_STORE_PASSWORD`: قيمة `storePassword` من `keystore.properties`
   - `DALILAK_KEY_ALIAS`: `dalilak`
   - `DALILAK_KEY_PASSWORD`: قيمة `keyPassword` من `keystore.properties`

   لتحويل المفتاح إلى base64 على ويندوز (PowerShell):
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("dalilak.jks"))`
   وعلى لينكس/ماك: `base64 -w0 dalilak.jks`
2. بعد إضافة الـ Secrets: Actions ← Build Dalilak APK ← Run workflow.
3. **الريبو Public.** المفتاح مش فيه، بس كود التطبيق ظاهر لأي حد. لو عايزه Private: Settings ← General ← Danger Zone ← Change visibility.
4. **احتفظ بنسخة من `dalilak.jks` و`keystore.properties` بره GitHub.** المفتاح ما يتولّدش تاني.
5. حالة الـ workflow ماقدرتش أتحقق منها (الـ token بتاع `gh` عندي غير صالح)، فنتيجة أول بناء لسه مجهولة. وحسب ملف الحالة، التطبيق نفسه ماتبناش ولا اتجرّب لسه.
