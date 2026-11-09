# Munhana — Arabic Graphing Calculator

Munhana is an Android graphing calculator with an Arabic (RTL) interface and a
dark purple theme. **Built with AI.**

- Plot functions, implicit equations and inequalities.
- Full-screen equation editor with live graph preview.
- Math keyboard with five categories and automatic bracket closing.
- Graph settings: grid, axis labels, arrows, X/Y axes, degrees/radians, zoom.
- Import a photo and read equations offline, then correct them manually.
- Export the graph as PNG, export equations as TXT, share and import.
- Works fully offline and needs no storage permission.

**Requirements:** Android 7.0 (API 24) or newer, `arm64-v8a` or `armeabi-v7a`.

**Download:** see [Releases](https://github.com/ZAIN-WZ/munhana/releases).
The latest version is `1.1`.

**Build from source:**

```bash
export ANDROID_HOME=/path/to/android-sdk   # platform 35 + build-tools 35.0.0
bash build.sh                               # output: dist/munhana-1.1.apk
```

The build script creates a local development key if none is provided.

**License:** [MIT](LICENSE).

---

# منحنى — راسم دوال عربي

منحنى تطبيق أندرويد لرسم الدوال والمعادلات، بواجهة عربية (RTL) وثيم داكن
بنفسجي. **صُنع بالذكاء الاصطناعي.**

- رسم الدوال والمعادلات الضمنية والمتباينات.
- محرّر معادلات بشاشة كاملة مع معاينة حيّة للرسم.
- لوحة رياضيات بخمس فئات وإغلاق تلقائي للأقواس.
- إعدادات رسم: الشبكة، أرقام المحاور، الأسهم، محورا X/Y، درجات/راديان،
  ومدى العرض.
- قراءة المعادلات من صورة دون اتصال، مع إمكانية التصحيح اليدوي.
- تصدير الرسم PNG، تصدير المعادلات TXT، مشاركة واستيراد.
- يعمل دون إنترنت ولا يحتاج صلاحية تخزين.

**المتطلبات:** أندرويد 7.0 (API 24) أو أحدث، ومعمارية `arm64-v8a` أو
`armeabi-v7a`.

**التحميل:** من صفحة [Releases](https://github.com/ZAIN-WZ/munhana/releases).
أحدث إصدار هو `1.1`.

**البناء من المصدر:**

```bash
export ANDROID_HOME=/path/to/android-sdk   # platform 35 + build-tools 35.0.0
bash build.sh                               # الناتج: dist/munhana-1.1.apk
```

ينشئ سكربت البناء مفتاح تطوير محليًا إن لم يوجد مفتاح.

**الترخيص:** [MIT](LICENSE).
