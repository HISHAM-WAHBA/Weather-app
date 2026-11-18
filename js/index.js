"use strict";
// Open-Meteo: free, no API key, guaranteed 7-day forecast + Arabic geocoding.
const GEO_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search";
const FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast";
const FORECAST_DAYS = 7;
const MAX_FORECAST_DAYS = 7;

// * HTML elements
const searchInput = document.querySelector("#searchInput");
const searchBtn = document.querySelector("#searchBtn");
const loadingSpinner = document.querySelector("#loadingSpinner");
const errorAlert = document.querySelector("#errorAlert");
const errorMessage = document.querySelector("#errorMessage");
const forecastContainer = document.querySelector("#forecastContainer");
const weatherSection = document.querySelector("#weatherSection");
const weatherEffects = document.querySelector("#weatherEffects");
const langToggle = document.querySelector("#langToggle");
const langLabel = document.querySelector("#langLabel");
const todayCard = document.querySelector("#todayDate");
const forecastPanel = document.querySelector(".forecast-panel");
const forecastGrid = document.querySelector("#forecastGrid");
const forecastLocation = document.querySelector("#forecastLocation");
const forecastRangeLabel = document.querySelector("#forecastRangeLabel");
const todaySkeleton = document.querySelector("#todaySkeleton");

const tempWrapper = document.querySelector("#tempWrapper");

//!  >>>>>>>>>>>>>>>>>>>>>today HTML elements<<<<<<<<<<<<<<<<<<<<<<<<<<<<
const dayName = document.querySelector("#dayName");
const dayNumber = document.querySelector("#dayNumber");
const todayMonthOfCity = document.querySelector("#todayMonthOfCity");
const todayLocation = document.querySelector("#todayLocation");
const todayTemp = document.querySelector("#todayTemp");
const todayConditionImg = document.querySelector("#todayConditionImg");
const todayWeatherDescription = document.querySelector("#todayWeatherDescription");
const todayHumidity = document.querySelector("#todayHumidity");
const todayWind = document.querySelector("#todayWind");
const todayWindDirection = document.querySelector("#todayWindDirection");
//! <<<<<<<<<<<<<<<<<<<<<<<<<<<<today HTML elements<<<<<<<<<<<<<<<<<<<<<<<<<<<<

// ================ Bilingual copy ================
const I18N = {
  en: {
    liveAnywhere: "Live weather, anywhere",
    searchPlaceholder: "Search for a city...",
    searchHint: "Try “Cairo”, “London” or “Tokyo”",
    search: "Search",
    loading: "Fetching weather data...",
    oops: "Oops!",
    genericError: "Something went wrong.",
    currentWeather: "CURRENT WEATHER",
    forecastOutlook: "7-DAY OUTLOOK",
    daysAhead: "The days ahead",
    forecastNote: "Temps shown as high / low. Bars reflect the daily range.",
    copyright:
      'Copyright 2025. Designed by <a href="https://hishamwahba.zya.me" target="_blank" rel="noopener noreferrer">Hisham Wahba.</a> All rights reserved ©',
    daysOutlook: "{n}-DAY OUTLOOK",
    tomorrow: "Tomorrow",
    rainChance: "rain",
    noForecast: "No further forecast available for this location.",
    errShort: "Please enter at least 3 characters to search.",
    errCity: "City not found. Please try a different name.",
    errNetwork: "Network error. Please check your connection and try again.",
    windUnit: "km/h",
    yourLocation: "Your location",
    switchLanguage: "English",
    pageTitle: "Weather app",
  },
  ar: {
    liveAnywhere: "طقس مباشر، في أي مكان",
    searchPlaceholder: "ابحث عن مدينة...",
    searchHint: "جرّب «القاهرة» أو «لندن» أو «طوكيو»",
    search: "بحث",
    loading: "جارٍ جلب بيانات الطقس...",
    oops: "عذراً!",
    genericError: "حدث خطأ ما.",
    currentWeather: "الطقس الحالي",
    forecastOutlook: "توقعات 7 أيام",
    daysAhead: "الأيام القادمة",
    forecastNote: "الدرجات القصوى / الدنيا. الأشرطة تعرض مدى التغير اليومي.",
    copyright: '© 2025 جميع الحقوق محفوظة. تصميم <a href="https://hishamwahba.zya.me" target="_blank" rel="noopener noreferrer">هشام وهبة.</a>',
    daysOutlook: "توقعات {n} أيام",
    tomorrow: "غداً",
    rainChance: "مطر",
    noForecast: "لا توجد توقعات إضافية لهذا الموقع.",
    errShort: "يرجى إدخال 3 أحرف على الأقل للبحث.",
    errCity: "لم يتم العثور على المدينة. جرّب اسماً آخر.",
    errNetwork: "خطأ في الشبكة. تحقق من اتصالك وحاول مجدداً.",
    windUnit: "كم/س",
    yourLocation: "موقعك الحالي",
    switchLanguage: "العربية",
    pageTitle: "تطبيق الطقس",
  },
};

// WMO weather codes -> readable text in both languages
const WMO_TEXT = {
  en: {
    0: "Clear sky", 1: "Mainly clear", 2: "Partly cloudy", 3: "Overcast",
    45: "Fog", 48: "Depositing rime fog",
    51: "Light drizzle", 53: "Moderate drizzle", 55: "Dense drizzle",
    56: "Light freezing drizzle", 57: "Dense freezing drizzle",
    61: "Slight rain", 63: "Moderate rain", 65: "Heavy rain",
    66: "Light freezing rain", 67: "Heavy freezing rain",
    71: "Slight snow fall", 73: "Moderate snow fall", 75: "Heavy snow fall",
    77: "Snow grains",
    80: "Slight rain showers", 81: "Moderate rain showers",
    82: "Violent rain showers",
    85: "Slight snow showers", 86: "Heavy snow showers",
    95: "Thunderstorm", 96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
  },
  ar: {
    0: "صافٍ", 1: "صافٍ غالباً", 2: "غائم جزئياً", 3: "غائم",
    45: "ضباب", 48: "ضباب متجمد",
    51: "رذاذ خفيف", 53: "رذاذ متوسط", 55: "رذاذ كثيف",
    56: "رذاذ متجمد خفيف", 57: "رذاذ متجمد كثيف",
    61: "مطر خفيف", 63: "مطر متوسط", 65: "مطر غزير",
    66: "مطر متجمد خفيف", 67: "مطر متجمد غزير",
    71: "تساقط ثلج خفيف", 73: "تساقط ثلج متوسط", 75: "تساقط ثلج غزير",
    77: "حبيبات ثلج",
    80: "زخات مطر خفيفة", 81: "زخات مطر متوسطة",
    82: "زخات مطر عنيفة",
    85: "زخات ثلج خفيفة", 86: "زخات ثلج غزيرة",
    95: "عاصفة رعدية", 96: "عاصفة رعدية مع برد خفيف",
    99: "عاصفة رعدية مع برد غزير",
  },
};

const COMPASS = [
  "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
  "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
];

const WIND_DIR_AR = {
  N: "ش", NNE: "ش ش م", NE: "ش م", ENE: "ش م ش", E: "ش", ESE: "ش م ج",
  SE: "ج ش", SSE: "ج ج ش", S: "ج", SSW: "ج ج غ", SW: "ج غ", WSW: "غ ج غ",
  W: "غ", WNW: "غ ش غ", NW: "ش غ", NNW: "ش ش غ",
};

// ^app variables
let currentLang = localStorage.getItem("weather-lang") === "ar" ? "ar" : "en";
let weatherData;
let currentQuery = null;
let lastFetchedQuery = null;
let searchDebounceTimer;

const THEME_PREFIX = "theme-";
const ALL_THEMES = [
  "theme-clear-day", "theme-clear-night", "theme-clear-sunset",
  "theme-cloudy-day", "theme-cloudy-night", "theme-rainy-day",
  "theme-rainy-night", "theme-snowy-day", "theme-snowy-night",
  "theme-foggy", "theme-thunder",
];

// ================ Helpers ================
function t(key, vars = {}) {
  let text = I18N[currentLang]?.[key] ?? I18N.en[key] ?? key;
  Object.entries(vars).forEach(([name, value]) => {
    text = String(text).replaceAll(`{${name}}`, value);
  });
  return text;
}

function replayAnimation(el, ...cls) {
  if (!el) return;
  el.classList.remove(...cls);
  void el.offsetWidth;
  el.classList.add(...cls);
}

function replayCardAnimations() {
  replayAnimation(todayCard, "card-animate", "card-animate-1");
  replayAnimation(forecastPanel, "card-animate", "card-animate-2");
}

function escapeHTML(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function parseApiDate(dateString) {
  const [year, month, day] = String(dateString).split("-").map(Number);
  return new Date(year, month - 1, day);
}

function dateLocale() {
  return currentLang === "ar" ? "ar-EG-u-ca-gregory-nu-latn" : "en-US";
}

function weekday(date) {
  return date.toLocaleDateString(dateLocale(), { weekday: "long" });
}

function shortDate(date) {
  return date.toLocaleDateString(dateLocale(), { month: "short", day: "numeric" });
}

function windCompass(degrees) {
  const index = Math.round((((Number(degrees) || 0) % 360) + 360) % 360 / 22.5) % 16;
  return COMPASS[index];
}

function wmoText(code, lang) {
  return WMO_TEXT[lang]?.[code] || (lang === "ar" ? "غير معروف" : "Unknown");
}

// Fully local SVG icons (no external image requests, no broken icons offline).
function weatherIconDataUrl(kind, isDay) {
  const orb = isDay
    ? `<circle cx="34" cy="32" r="15" fill="url(#sunGrad)"/>
       <g stroke="#fbbf24" stroke-width="4" stroke-linecap="round">
         <path d="M34 8v7M34 49v7M10 32h7M51 32h7M17 15l5 5M46 44l5 5M51 15l-5 5M22 44l-5 5"/>
       </g>`
    : `<path d="M46 16a19 19 0 1 0 13 32A21 21 0 0 1 46 16Z" fill="url(#moonGrad)"/>
       <g fill="#e0e7ff"><circle cx="18" cy="18" r="2.4"/><circle cx="68" cy="22" r="2"/><circle cx="22" cy="48" r="1.8"/><circle cx="78" cy="44" r="1.6"/></g>`;

  const cloud = `<path d="M28 72a15 15 0 0 1 1-30 20 20 0 0 1 37 5 13 13 0 0 1-3 25Z" fill="url(#cloudGrad)"/>`;
  const smallCloud = `<path d="M32 66a11 11 0 0 1 1-22 15 15 0 0 1 28 4 10 10 0 0 1-2 18Z" fill="url(#cloudGrad)"/>`;

  let art = "";
  if (kind === "clear") art = orb;
  else if (kind === "mostly-clear") art = orb + smallCloud;
  else if (kind === "partly-cloudy") art = orb + cloud;
  else if (kind === "cloudy") {
    art = `<path d="M20 74a18 18 0 0 1 2-36 24 24 0 0 1 45 6 16 16 0 0 1-4 30Z" fill="url(#cloudGrad)"/>`;
  } else if (kind === "fog") {
    art = cloud + `<g stroke="#cbd5e1" stroke-width="4" stroke-linecap="round"><path d="M22 80h50M30 89h42M20 98h50"/></g>`;
  } else if (kind === "drizzle") {
    art = cloud + `<g stroke="#38bdf8" stroke-width="3.5" stroke-linecap="round"><path d="M36 78l-3 10M52 78l-3 10M68 78l-3 10"/></g>`;
  } else if (kind === "rain") {
    art = cloud + `<g stroke="#38bdf8" stroke-width="4" stroke-linecap="round"><path d="M35 78l-5 14M53 78l-5 14M71 78l-5 14"/></g>`;
  } else if (kind === "snow") {
    art = cloud + `<g fill="#e0f2fe"><circle cx="35" cy="84" r="4"/><circle cx="53" cy="88" r="4"/><circle cx="70" cy="84" r="4"/><circle cx="61" cy="96" r="3"/></g>`;
  } else if (kind === "thunder") {
    art = `<path d="M20 72a18 18 0 0 1 2-36 24 24 0 0 1 45 6 16 16 0 0 1-4 30Z" fill="url(#stormGrad)"/>
      <path d="M55 62 38 86h13l-5 18 22-28H54l7-14Z" fill="#fbbf24"/>`;
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
    <defs>
      <linearGradient id="sunGrad" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fde68a"/><stop offset="1" stop-color="#f59e0b"/></linearGradient>
      <linearGradient id="moonGrad" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#e0e7ff"/><stop offset="1" stop-color="#818cf8"/></linearGradient>
      <linearGradient id="cloudGrad" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f8fafc"/><stop offset="1" stop-color="#94a3b8"/></linearGradient>
      <linearGradient id="stormGrad" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#64748b"/><stop offset="1" stop-color="#334155"/></linearGradient>
    </defs>
    ${art}
  </svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function wmoIcon(code, isDay) {
  const day = isDay !== false;
  let kind = "clear";
  if (code === 1) kind = "mostly-clear";
  else if (code === 2) kind = "partly-cloudy";
  else if (code === 3) kind = "cloudy";
  else if (code === 45 || code === 48) kind = "fog";
  else if (code >= 51 && code <= 57) kind = "drizzle";
  else if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) kind = "rain";
  else if ((code >= 71 && code <= 77) || code === 85 || code === 86) kind = "snow";
  else if (code >= 95) kind = "thunder";
  return weatherIconDataUrl(kind, day);
}

function categoryFromCode(code) {
  if (code >= 95) return "thunder";
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return "snowy";
  if ((code >= 51 && code <= 57) || (code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "rainy";
  if (code === 45 || code === 48) return "foggy";
  if (code >= 2 && code <= 3) return "cloudy";
  return "clear";
}

// ================ Language ================
function applyLanguage() {
  const isArabic = currentLang === "ar";
  document.documentElement.lang = currentLang;
  document.documentElement.dir = isArabic ? "rtl" : "ltr";
  document.title = t("pageTitle");

  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.dataset.i18n;
    if (key in I18N.en) el.textContent = t(key);
  });
  document.querySelectorAll("[data-i18n-html]").forEach((el) => {
    const key = el.dataset.i18nHtml;
    if (key in I18N.en) el.innerHTML = t(key);
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    const value = t(el.dataset.i18nPlaceholder);
    el.placeholder = value;
    el.setAttribute("aria-label", value);
  });

  // The button shows the language you can switch TO.
  if (langLabel) langLabel.textContent = isArabic ? "E" : "ع";
  if (langToggle) {
    langToggle.setAttribute("aria-label", isArabic ? "Switch to English" : "التبديل إلى العربية");
    langToggle.title = isArabic ? "English" : "العربية";
  }

  localStorage.setItem("weather-lang", currentLang);

  if (weatherData && !weatherData.error) {
    todayWeather();
    forecastWeather();
  }
}

// ================ Skeleton loader ================
function renderForecastSkeletons(count = 6) {
  if (!forecastGrid) return;
  forecastGrid.innerHTML = Array.from({ length: count })
    .map(
      (_, i) => `
      <div class="forecast-skeleton" style="--i: ${i}">
        <div class="d-flex justify-content-between">
          <div class="skeleton skeleton-text w-40"></div>
          <div class="skeleton skeleton-text w-25"></div>
        </div>
        <div class="skeleton skeleton-img-sm mx-auto my-3"></div>
        <div class="skeleton skeleton-text-xl mx-auto"></div>
        <div class="skeleton skeleton-text mx-auto w-50"></div>
        <div class="skeleton skeleton-bar mx-auto mt-3"></div>
      </div>`,
    )
    .join("");
}

// ================ Condition + weather effects ================
function isDayTime(data) {
  return Boolean(data && data.current && data.current.is_day === 1);
}

function detectConditionCategory(data) {
  return categoryFromCode(Number(data?.current?.condition?.code));
}

function getWeatherVariant(data) {
  const category = detectConditionCategory(data);
  const day = isDayTime(data);
  if (category === "foggy" || category === "thunder") return category;
  if (category === "clear") {
    if (!day) return "clear-night";
    const hour = Number(String(data?.location?.localtime || "").slice(11, 13));
    return hour < 8 || hour >= 16 ? "clear-sunset" : "clear-day";
  }
  return `${category}-${day ? "day" : "night"}`;
}

function applyWeatherEffects(variant) {
  if (!weatherEffects) return;
  weatherEffects.className = "weather-effects";
  void weatherEffects.offsetWidth;
  weatherEffects.classList.add(`fx-${variant}`);
}

function applyWeatherTheme(data) {
  if (!weatherSection) return;
  ALL_THEMES.forEach((theme) => weatherSection.classList.remove(theme));
  if (!data || !data.current) {
    if (weatherEffects) weatherEffects.className = "weather-effects";
    return;
  }
  const variant = getWeatherVariant(data);
  weatherSection.classList.add(THEME_PREFIX + variant);
  applyWeatherEffects(variant);
}

// ================ Loading/Error UI ================
function showLoading() {
  if (loadingSpinner) loadingSpinner.classList.remove("d-none");
  if (errorAlert) errorAlert.classList.add("d-none");
  if (forecastContainer) forecastContainer.classList.add("is-loading");
  if (todaySkeleton) todaySkeleton.classList.remove("d-none");
  renderForecastSkeletons();
}

function hideLoading() {
  if (loadingSpinner) loadingSpinner.classList.add("d-none");
  if (forecastContainer) forecastContainer.classList.remove("is-loading");
  if (todaySkeleton) todaySkeleton.classList.add("d-none");
}

function showError(msg) {
  if (errorAlert) {
    errorMessage.textContent = msg || t("genericError");
    errorAlert.classList.remove("d-none");
  }
  if (weatherEffects) weatherEffects.className = "weather-effects";
  hideLoading();
}

function hideError() {
  if (errorAlert) errorAlert.classList.add("d-none");
}

// ================ Data (Open-Meteo) ================
function normalizeForecast(raw, name, country, lang) {
  const current = raw.current;
  const daily = raw.daily;
  const currentCode = Number(current.weather_code);
  const localDate = String(current.time).slice(0, 10);
  const localHour = String(current.time).slice(11, 16);

  return {
    location: {
      name,
      country,
      localtime: `${localDate} ${localHour}`,
    },
    current: {
      temp_c: current.temperature_2m,
      humidity: current.relative_humidity_2m,
      wind_kph: current.wind_speed_10m,
      wind_dir: windCompass(current.wind_direction_10m),
      is_day: current.is_day,
      condition: {
        code: currentCode,
        text: wmoText(currentCode, lang),
        icon: wmoIcon(currentCode, current.is_day === 1),
      },
    },
    forecast: {
      forecastday: daily.time.map((date, index) => {
        const code = Number(daily.weather_code[index]);
        return {
          date,
          day: {
            maxtemp_c: daily.temperature_2m_max[index],
            mintemp_c: daily.temperature_2m_min[index],
            chance_of_rain: daily.precipitation_probability_max?.[index] ?? 0,
            condition: {
              code,
              text: wmoText(code, lang),
              icon: wmoIcon(code, true),
            },
          },
        };
      }),
    },
  };
}

async function getData(city, days = FORECAST_DAYS, lang = currentLang) {
  const query = String(city).trim();
  const coords = query.match(/^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$/);

  let latitude;
  let longitude;
  let name;
  let country = "";

  if (coords) {
    latitude = Number(coords[1]);
    longitude = Number(coords[2]);
    name = t("yourLocation");
  } else {
    const geoUrl =
      `${GEO_ENDPOINT}?name=${encodeURIComponent(query)}` +
      `&count=1&language=${lang}&format=json`;
    const geoResponse = await fetch(geoUrl);
    if (!geoResponse.ok) throw new Error(`Geocoding HTTP ${geoResponse.status}`);
    const geoData = await geoResponse.json();
    const place = geoData.results?.[0];
    if (!place) return { error: { message: t("errCity") } };
    latitude = place.latitude;
    longitude = place.longitude;
    name = place.name;
    country = place.country || "";
  }

  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    current:
      "temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code,is_day",
    daily:
      "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
    timezone: "auto",
    forecast_days: String(days),
  });

  const response = await fetch(`${FORECAST_ENDPOINT}?${params.toString()}`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const raw = await response.json();
  if (!raw.current || !raw.daily?.time?.length) {
    return { error: { message: t("errCity") } };
  }
  return normalizeForecast(raw, name, country, lang);
}

async function startApp(city, force = false) {
  const query = String(city).trim();
  if (!query) return;
  if (!force && query.toLowerCase() === lastFetchedQuery && weatherData) return;

  currentQuery = query;
  hideError();
  showLoading();
  try {
    weatherData = await getData(query, FORECAST_DAYS, currentLang);
    if (!weatherData.error) {
      lastFetchedQuery = query.toLowerCase();
      applyWeatherTheme(weatherData);
      replayCardAnimations();
      todayWeather();
      forecastWeather();
    } else {
      showError(weatherData.error.message || t("errCity"));
    }
  } catch (err) {
    console.error("Network error:", err);
    showError(t("errNetwork"));
  } finally {
    hideLoading();
  }
}

// ================ Today card ================
function todayWeather() {
  const { location, current } = weatherData;
  const localDate = new Date(location.localtime.replace(" ", "T"));

  dayName.textContent = weekday(localDate);
  dayNumber.textContent = localDate.getDate();
  todayMonthOfCity.textContent = localDate.toLocaleDateString(dateLocale(), {
    month: "long",
  });
  todayLocation.textContent = location.name;
  todayTemp.textContent = Math.round(current.temp_c);
  todayConditionImg.src = current.condition.icon;
  todayWeatherDescription.textContent = current.condition.text;
  todayHumidity.textContent = `${current.humidity}%`;
  todayWind.textContent = `${Math.round(current.wind_kph)} ${t("windUnit")}`;
  todayWindDirection.textContent =
    currentLang === "ar"
      ? WIND_DIR_AR[current.wind_dir] || current.wind_dir
      : current.wind_dir;

  replayAnimation(tempWrapper, "weather-temp-animate");
  replayAnimation(todayConditionImg, "weather-icon-animate");
  replayAnimation(todayLocation, "weather-text-animate");
  replayAnimation(todayWeatherDescription, "weather-text-animate");
}

// ================ Upcoming days (7-day grid) ================
function forecastWeather() {
  const { location, forecast } = weatherData;
  const allDays = (forecast?.forecastday || []).slice(0, MAX_FORECAST_DAYS);
  const upcoming = allDays.slice(1);

  if (forecastLocation) {
    const country = location.country ? `, ${location.country}` : "";
    forecastLocation.innerHTML = `<i class="fa-solid fa-location-dot me-1"></i>${escapeHTML(location.name)}${escapeHTML(country)}`;
  }
  if (forecastRangeLabel) {
    const total = allDays.length || upcoming.length + 1;
    forecastRangeLabel.textContent =
      total > 1 ? t("daysOutlook", { n: total }) : t("forecastOutlook");
  }

  if (!upcoming.length) {
    forecastGrid.innerHTML = `<p class="forecast-empty mb-0">${t("noForecast")}</p>`;
    return;
  }

  const lows = upcoming.map((d) => d.day.mintemp_c);
  const highs = upcoming.map((d) => d.day.maxtemp_c);
  const globalMin = Math.min(...lows);
  const globalMax = Math.max(...highs);
  const span = Math.max(globalMax - globalMin, 1);
  const isArabic = currentLang === "ar";

  forecastGrid.innerHTML = upcoming
    .map((dayData, index) => {
      const date = parseApiDate(dayData.date);
      const day = dayData.day;
      const start = ((day.mintemp_c - globalMin) / span) * 100;
      const width = Math.max(((day.maxtemp_c - day.mintemp_c) / span) * 100, 10);
      const barPos = isArabic
        ? `right: ${start.toFixed(1)}%`
        : `left: ${start.toFixed(1)}%`;

      return `
        <article class="forecast-card" style="--i: ${index}">
          <div class="forecast-card__head">
            <span class="forecast-card__day">${index === 0 ? escapeHTML(t("tomorrow")) : escapeHTML(weekday(date))}</span>
            <span class="forecast-card__date">${escapeHTML(shortDate(date))}</span>
          </div>
          <img
            class="forecast-card__icon"
            src="${escapeHTML(day.condition.icon)}"
            alt="${escapeHTML(day.condition.text)}"
            loading="lazy"
          />
          <div class="forecast-card__temps">
            <span class="forecast-card__max">${Math.round(day.maxtemp_c)}&deg;</span>
            <span class="forecast-card__min">${Math.round(day.mintemp_c)}&deg;</span>
          </div>
          <p class="forecast-card__condition">${escapeHTML(day.condition.text)}</p>
          <div class="forecast-card__rain">
            <i class="fa-solid fa-droplet"></i> ${Math.round(day.chance_of_rain || 0)}% ${escapeHTML(t("rainChance"))}
          </div>
          <div class="forecast-card__bar">
            <span style="${barPos}; width: ${width.toFixed(1)}%"></span>
          </div>
        </article>`;
    })
    .join("");
}

// ================ Init & events ================
applyLanguage();

if (langToggle) {
  langToggle.addEventListener("click", function () {
    currentLang = currentLang === "en" ? "ar" : "en";
    applyLanguage();
    startApp(currentQuery || "Cairo", true);
  });
}

navigator.geolocation.getCurrentPosition(
  function (location) {
    startApp(`${location.coords.latitude},${location.coords.longitude}`);
  },
  function (error) {
    console.warn("Could not access location, defaulting to Cairo");
    startApp("Cairo");
  },
);

function handleSearch() {
  const query = searchInput.value.trim();
  if (query.length >= 3) {
    if (query.toLowerCase() !== lastFetchedQuery) startApp(query);
  } else if (query.length > 0) {
    showError(t("errShort"));
  }
}

searchInput.addEventListener("input", function () {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(handleSearch, 500);
});

searchInput.addEventListener("keydown", function (e) {
  if (e.key === "Enter") {
    e.preventDefault();
    clearTimeout(searchDebounceTimer);
    handleSearch();
  }
});

if (searchBtn) {
  searchBtn.addEventListener("click", function (e) {
    e.preventDefault();
    clearTimeout(searchDebounceTimer);
    handleSearch();
  });
}
