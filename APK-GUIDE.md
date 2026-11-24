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
- الأبعاد متظبطة في `css/style.css` ومتزامنة مع نسخة التطبيق في `assets/www/css/style.css`: زر اللغة 48px (46px على الموبايل و44px على الشاشات الصغيرة) بـ min-size عشان ما يتقلش، والحرف `E`/`ع` متوسّط بصرياً جوّه الدايرة، وأرقام الحرارة بـ `tabular-nums` ومسافات ثابتة عشان ما تتقصّش. أيقونات التواصل جوّه التطبيق اتحجّمت بحيث تفضل واضحة جنب زر اللغة.
- **رقم الإصدار:** `MainActivity` بيعطي `BuildConfig.VERSION_NAME` للصفحة كـ `window.APP_VERSION`، و`app-skin.js` بيضيف لوحة صغيرة `weather app vX.Y` في آخر الشاشة — الموقع ما بيشوفهاش.
- **الـ scrollbar:** مخفي جوّه التطبيق بس (CSS في `app-skin.js` + `setVerticalScrollBarEnabled(false)` في `MainActivity`) — الموقع لسه بالـ scrollbar العادي.
- **الوقت:** بطاقة الوقت بتعرض توقيت المدينة اللي اتعمل لها بحث (من `timezone` / `utc_offset_seconds` في رد Open-Meteo) مش توقيت جهاز المستخدم، والعنوان بيبقى `LOCAL TIME IN <city>` / `الوقت في <city>`.

## هل التعديلات تظهر في التطبيق علطول؟
- تعديلات الموقع (`css/` و`js/`) بتنزل على Vercel فوراً والتطبيق بيحمّل الموقع الحي بـ `LOAD_NO_CACHE`، فبتظهر أول ما تفتح التطبيق تاني من غير تثبيت APK جديد.
- تعديلات التطبيق فقط (`app-skin.js`، `MainActivity`، الصلاحيات، رقم الإصدار) محتاجة APK جديد يتثبّت مرة واحدة. بعدها أي تعديل موقع بيظهر فوراً زي ما هو مطلوب.

## التحديث جوّه التطبيق
- كل بناء ناجح ينشئ GitHub Release بالـ tag `v<versionName>` ويرفع `weather-app-v<version>.apk` فيه تلقائياً (اسم الملف بيحمل رقم النسخة).
- عند فتح التطبيق بيقارن إصدار المثبّت بآخر Release: لو فيه إصدار أحدث يظهر "`New version available`" مع زر **Update now** و**Later**.
- الضغط على **Update now** بيحمّل الـ APK **داخل التطبيق** عبر `DownloadManager`، وبعد اكتمال التحميل Android بيعرض شاشة التثبيت العادية — المستخدم يضغط "تثبيت" بس.
- التطبيق نفسه **ما بيطلبش** صلاحية `REQUEST_INSTALL_PACKAGES`، فأنظمة حماية الموبايل ما ترفضهوش.
- لو التحميل جوّه التطبيق فشل لأي سبب، بيحصل fallback لفتح رابط التحميل في المتصفح.
- أول تثبيت/تحديث قد يحتاج تفعيل "Install unknown apps" للتطبيق — ده إعداد أندرويد عادي مش تبع نظامنا.

## صلاحيات التطبيق
- `INTERNET`: لجلب الطقس من Open-Meteo وتحميل الموقع.
- `ACCESS_NETWORK_STATE`: لمعرفة حالة الاتصال قبل تحميل الموقع.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: عشان يفتح المدينة تلقائياً زي المتصفح (بيطلب إذن من المستخدم أول مرة). **مفيش موقع في الخلفية.**
- `POST_NOTIFICATIONS` (أندرويد 13+): بيُطلب فقط لما المستخدم يضغط زر الجرس لتفعيل الإشعارات.
- `RECEIVE_BOOT_COMPLETED`: لإعادة جدولة الإشعار بعد إعادة تشغيل الموبايل.
- **مفيش `REQUEST_INSTALL_PACKAGES`** — التحديث بيفتح المتصفح زي ما هو موضّح فوق.

## الإشعارات اليومية (7 صباحاً و10 مساءً)
- زر الجرس ظاهر في navbar التطبيق فقط (مش في الموقع) — اضغطه لتفعيل أو إيقاف الإشعارين.
- التطبيق بيحفظ **آخر مدينة بحثت عنها** (الاسم، الدولة، الإحداثيات، اللغة) محلياً في `SharedPreferences`، وبيستخدمها لجلب الطقس من Open-Meteo وقت الإشعار.
- الجسر بين JS والأندرويد اسمه `window.WeatherBridge` وبيُحقن في التطبيق فقط. نفس `js/index.js` على الموقع بيشتغل عادي لأن الجسر مش موجود هناك (no-op).
- الجدولة بـ `AlarmManager` (`NotificationScheduler.java`) مع `WeatherAlarmReceiver` و`BootReceiver`، والمنبّه بيعيد جدولة نفسه بعد كل إشعار وبعد إعادة تشغيل الموبايل. التوقيت تقريبي بسبب Doze/البطارية (من غير صلاحية exact alarms اللي Play بيقيّدها).
- الإشعار متوقف افتراضياً؛ ومفيش أي بيانات بتتبعت لخادم خاص بنا.

## الإعلانات (AdMob) — معطّلة مؤقتاً
- الإعلانات **متوقفة حالياً** لأن الـ flag `ENABLE_ADS=false` (الافتراضي). ما فيش طلبات إعلانات ولا تهيئة AdMob ولا شاشة موافقة UMP طالما هي متوقفة.
- لما تبقى جاهز (حساب AdMob + طريقة استلام دفع): أضف GitHub Secrets `ENABLE_ADS=true` ومعاها `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID` بالمعرّفات الحقيقية، واعمل Run workflow — من غير أي تعديل كود.
- ملفات ذات صلة: `MainActivity.java` (initAds/loadBanner/loadInterstitial) و `AndroidManifest.xml` (بيانات الـ App ID) و `res/layout/activity_main.xml` (الـ AdView).

## Google Play
- البناء بيطلع **AAB** للـ flavor بتاع Play كمان: المهمة `bundlePlayRelease` والملف في artifact اسمه `weather-app-aab`.
- **Product flavors:**
  - `sideload`: التحديث الذاتي من GitHub شغال (برابط تحميل في المتصفح) — ده الـ APK اللي بنوزّعه يدوياً.
  - `play`: التحديث الذاتي **متوقف** (سياسة Play تمنع التحديث من بره المتجر). نفس اسم الحزمة والتوقيع عشان ترقية المستخدمين من نسخة الـ sideload من غير مسح.
- الرفع الآلي بيحصل لو اضفت Secret اسمه `PLAY_SERVICE_ACCOUNT_JSON` (محتوى ملف الـ service account JSON كامل). لو مش موجود، خطوة الرفع تتخطى والبناء يكمل عادي.
- الرفع بيحصل على المسار `internal` بحالة `draft`؛ بعدها تروح الـ Console وتنشئ closed testing (12 مختبِر / 14 يوم) ثم تنشر.
- لازم في الـ Console تختار **App signing by Google Play** وتستخدم نفس مفتاح `HW.jks` كـ app signing key عشان التوقيع يفضل متطابق.
- صفحة `privacy.html` متاحة على `https://weather-app-sooty-phi-31.vercel.app/privacy.html` ومتصل من footer الموقع — استخدم الرابط ده في خانة Privacy Policy في الـ Console.
- لازم تكمل في الـ Console: Data Safety (بيانات محلية + إعلانات)، تصنيف المحتوى، لقطات شاشة، أيقونة 512px، ووصف المتجر.

## ملاحظات
- الـ APK **release** وموقّع بمفتاح ثابت: ملفه `android/app/HW.jks` والـ alias الخاص به `HW`.
  أي تحديث مستقبلي يتثبّت فوق النسخة القديمة مباشرة من غير ما المستخدم يمسح التطبيق.
- تحذير "مطور غير موثوق" بيظهر لأي APK متثبّتش بره Google Play، ومش بيمنع التثبيت — اضغط "تثبيت على أي حال".
- الحد الأدنى أندرويد 6.0 (API 23)، والهدف API 35.
- زر الرجوع بيرجع لصفحة سابقة جوّه التطبيق، وبيخرج منه لو مفيش صفحات سابقة.
