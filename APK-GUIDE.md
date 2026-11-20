# دليل تطبيق الأندرويد (APK)

## الفكرة
التطبيق ليس إعادة كتابة للمشروع — هو **WebView** يحمل نفس موقعك على Vercel:
`https://weather-app-sooty-phi-31.vercel.app`

يعني أي تعديل تعمله في `index.html` / `css` / `js` وتعمل push للريبو:
- الموقع بتاعك على Vercel يتحدث تلقائياً.
- التطبيق يحمل نفس الرابط، فالتعديل يظهر فيه **بدون ما تعمل APK جديد**.

النسخة في `android/app/src/main/assets/www/` هي نسخة احتياطية بتستخدم لو النت قطع.

## أين أعدل الكود؟
دائماً في **جذر المشروع** زي ما اعتدت:
- `index.html`
- `css/style.css`
- `js/index.js`
- `imgs/` و `webfonts/`

لو حابب تحدّث النسخة المحلية الاحتياطية أيضاً:
```bash
npm run sync     # نسخة واحدة
npm run watch    # مراقبة تلقائية أثناء التطوير (Ctrl+C للإيقاف)
```

## كيف أبني APK؟
1. اعمل commit و push على `main` (أو اضغط Run workflow من تبويب Actions).
2. افتح **Actions → Build Weather APK** و حمّل الـ artifact `weather-app`.
3. افتح الـ zip واستخرج `weather app.apk` وارفعه على منصتك أو ثبّته على جهازك.

> البناء مجاني بالكامل على GitHub Actions — مش محتاج Android Studio ولا JDK على جهازك.

## أين أغيّر رابط الموقع أو اسم التطبيق؟
- الرابط: `android/app/src/main/java/com/hishamwahba/weatherapp/MainActivity.java` → الثابت `REMOTE_URL`.
- اسم التطبيق: `android/app/src/main/res/values/strings.xml` → `app_name`.
- الأيقونة: `android/app/src/main/res/mipmap-*/ic_launcher.png`.

## تعديلات التطبيق فقط (بدون الموقع)
- **شاشة البداية (Splash):** اللوجو وكلمة `weather app` في `res/layout/activity_main.xml` و`res/values/strings.xml`.
- **إخفاء الـ footer ونقل التواصل للـ navbar:** `android/app/src/main/assets/app-skin.js`.
  التطبيق يحقنه في الصفحة بعد التحميل، فالموقع نفسه ما يتعدّلش والـ auto-update يفضل شغال.
- الملف `app-skin.js` خارج مجلد `www`، فـ `npm run sync` مش هيشيله.

## صلاحيات التطبيق
- `INTERNET`: لجلب الطقس من Open-Meteo وتحميل الموقع.
- `ACCESS_NETWORK_STATE`: لمعرفة حالة الاتصال قبل تحميل الموقع.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: عشان يفتح المدينة تلقائياً زي المتصفح (بيطلب إذن من المستخدم أول مرة).

## ملاحظات
- الـ APK **release** وموقّع بمفتاح ثابت: ملفه `android/app/HW.jks` والـ alias الخاص به `HW`.
  أي تحديث مستقبلي يتثبّت فوق النسخة القديمة مباشرة من غير ما المستخدم يمسح التطبيق.
- تحذير "مطور غير موثوق" بيظهر لأي APK متثبّتش بره Google Play، ومش بيمنع التثبيت — اضغط "تثبيت على أي حال".
- الحد الأدنى أندرويد 6.0 (API 23)، والهدف API 35.
- زر الرجوع بيرجع لصفحة سابقة جوّه التطبيق، وبيخرج منه لو مفيش صفحات سابقة.
