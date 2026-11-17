# UPLOAD_STATUS.md — دليلك (Dalilak)

## تاريخ ووقت المحاولة
2026-11-17 — 21:46 (توقيت القاهرة)

## الريبو والـ branch
- الريبو: https://github.com/emyadel8150-cmyk/DALILAK-project
- الـ branch: `main`
- حالة الريبو وقت الفحص: فاضي (مفيش أي branches)
- الرؤية الحالية للريبو: **Public** (ملف الحالة بيقول إن المقصود Private)

## الحالة
**Prepared only (not uploaded)** — المشروع متجهّز محليًا ومتفحوص، ولسه ماترفعش حاجة على GitHub.

## الملفات اللي هتترفع (30 ملف)
- `.github/workflows/build.yml`
- `.gitignore`
- `DALILAK_STATE.md`, `DALILAK_STATE_v1.0.md`, `DALILAK_TODO.md`, `PROJECT_STATUS.md`
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/schemas/.gitkeep`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/casper/dalilak/`: `MainActivity.kt`, `AppViewModel.kt`, `Translations.kt`, `ai/AiModels.kt`, `ai/GeminiClient.kt`, `data/AppSettings.kt`, `data/Backup.kt`, `data/Models.kt`, `data/Repository.kt`, `ui/Common.kt`, `ui/Screens.kt`
- `app/src/main/res/`: `drawable/ic_fg.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-anydpi-v26/ic_launcher_round.xml`, `values/colors.xml`, `values/strings.xml`, `values/themes.xml`
- `UPLOAD_STATUS.md` (الملف ده)

## الملفات المستثناة بـ `.gitignore`
- `dalilak.jks` (مفتاح التوقيع)
- `keystore.properties` (باسوردات المفتاح)
- وأنماط عامة: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `*.jks`, `*.keystore`, `*.apk`, `*.aab`, `*.ap_`, `.cxx/`, `.externalNativeBuild/`, `captures/`, `.env`, `.env.*`, `.DS_Store`, `Thumbs.db`, `*.log`

## نتيجة فحص البيانات الحساسة
- لقيت `dalilak.jks` و`keystore.properties` جوه المشروع، و`.gitignore` القديم ماكانش بيستثنيهم. **اتضافوا للاستثناء وتأكدت بـ `git check-ignore` إنهم مش هيترفعوا.**
- سكان على أنماط مفاتيح API وتوكنات GitHub ومفاتيح خاصة: **مفيش مفاتيح مكتوبة جوه الكود.** المطابقات الوحيدة كانت قراءة الباسورد من `keystore.properties` في `app/build.gradle.kts` (سطر 29 و31).
- مفيش `local.properties` ولا `.env` ولا `google-services.json` ولا APK/AAB.
- مفتاح Gemini بيتخزّن مشفّر على الموبايل وبيتكتب من الإعدادات، فمش في الكود.

## الأوامر
بعد ما تحسم موضوع التوقيع تحت، شغّل من جوه فولدر المشروع:

```bash
git init -b main
git add .
git status          # اتأكد إن dalilak.jks و keystore.properties مش ظاهرين
git commit -m "Initial commit: Dalilak v1.0"
git remote add origin https://github.com/emyadel8150-cmyk/DALILAK-project.git
git push -u origin main
```

وللتحقق بعد الرفع:

```bash
git ls-remote origin
git ls-files | grep -E "\.jks$|keystore\.properties" || echo "clean"
```

الأوامر اللي اتشغلت فعلًا عندي (على نسخة الفحص بس): `git init -b main`، `git add -A`، `git check-ignore -v`، `git ls-files`، `git ls-remote` (للريبو).

## مشاكل لازم تتحل وإيه اللي فاضل
1. **البناء على GitHub Actions هيقع بدون حل:** `app/build.gradle.kts` بيقرا `keystore.properties` من غير شرط، وبيوقّع الـ debug والـ release بيه، والـ workflow مفيهوش Secrets. بما إن الملف مستثنى، أول بناء هيفشل. الحل المقترح: نخزّن المفتاح والباسوردات كـ GitHub Secrets ونعدّل `build.yml` بس عشان يرجّعهم قبل البناء (من غير لمس كود التطبيق).
2. **الريبو Public:** مفتاح التوقيع ما ينفعش يترفع عليه أبدًا. لو عايز ترفع المفتاح نفسه لازم الريبو يبقى Private الأول، وده مش موصى بيه.
3. **احتفظ بنسخة من `dalilak.jks` و`keystore.properties` بره GitHub.** ملف الحالة بيقول إن المفتاح ما يتغيرش ولا يتولّد تاني، ولو ضاع مش هتقدر تحدّث التطبيق بنفس التوقيع.
4. **البناء والتجربة على الموبايل ماحصلوش لسه** (حسب ملف الحالة).
