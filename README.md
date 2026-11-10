# Munhana — Arabic Graphing Calculator

Munhana is an Android graphing calculator with an Arabic (RTL) interface and a
dark purple theme. **Built with AI.**

- Plot functions, implicit equations and inequalities.
- Full-screen equation editor with live graph preview.
- Equation editor with a live written-form preview above the input, a
  calculator-style six-column keyboard with a fraction/root/power column, and
  undo plus cursor keys.
- Equations are shown as written: implicit multiplication (`2x`, `3x₁`, `x₁x₂`)
  and real symbols (`x₁`, `x²`, `≤`, `≥`) instead of code-like text.
- Variable labels, including subscripts (`x₁`, `x₂`) or Arabic names.
- Graph settings: grid, axis labels, arrows, X/Y axes, degrees/radians, zoom.
- Import a photo and read equations offline, then correct them manually.
  Cropping is manual, and the handwriting reader groups lines automatically.
- Export the graph as PNG with the equations written under it, export as TXT,
  and share a `.munhana` project that opens directly in Munhana.
- Works fully offline and needs no storage permission.

![Munhana home screen](docs/screenshots/home.png)
![Equation editor](docs/screenshots/editor.png)

**Requirements:** Android 7.0 (API 24) or newer, `arm64-v8a` or `armeabi-v7a`.

**Download:** see [Releases](https://github.com/ZAIN-WZ/munhana/releases).
The latest version is `1.2`.

**Build from source:**

```bash
export ANDROID_HOME=/path/to/android-sdk   # platform 35 + build-tools 35.0.0
bash build.sh                               # output: dist/munhana-1.2.apk
```

The build script creates a local development key if none is provided.

**License:** [MIT](LICENSE).

---

# منحنى — راسم دوال عربي

منحنى تطبيق أندرويد لرسم الدوال والمعادلات، بواجهة عربية (RTL) وثيم داكن
بنفسجي. **صُنع بالذكاء الاصطناعي.**

- رسم الدوال والمعادلات الضمنية والمتباينات.
- محرّر معادلات بشاشة كاملة مع معاينة حيّة للرسم.
- محرّر مع معاينة نصية للمعادلة كما كُتبت فوق حقل الإدخال، ولوحة مفاتيح
  بستة أعمدة تضم عمود القوالب (كسر، جذر، أس) مع تراجع وأسهم للمؤشر.
- عرض المعادلات كما كُتبت: ضرب ضمني (`2x`، `3x₁`، `x₁x₂`) ورموز رياضية
  حقيقية (`x₁`، `x²`، `≤`، `≥`) بدل صيغة تشبه الشيفرة.
- تسمية المتغيرات، مع رموز سفلية (`x₁`، `x₂`) أو أسماء عربية.
- إعدادات رسم: الشبكة، أرقام المحاور، الأسهم، محورا X/Y، درجات/راديان،
  ومدى العرض.
- قراءة المعادلات من صورة دون اتصال، مع إمكانية التصحيح اليدوي.
  القص يدوي، وقارئ الكتابة اليدوية يجمع الأسطر تلقائيًا.
- تصدير الرسم PNG مع كتابة المعادلات أسفله، وتصدير TXT، ومشاركة ملف
  `.munhana` يُفتح مباشرة في منحنى.
- يعمل دون إنترنت ولا يحتاج صلاحية تخزين.

**المتطلبات:** أندرويد 7.0 (API 24) أو أحدث، ومعمارية `arm64-v8a` أو
`armeabi-v7a`.

**التحميل:** من صفحة [Releases](https://github.com/ZAIN-WZ/munhana/releases).
أحدث إصدار هو `1.2`.

**البناء من المصدر:**

```bash
export ANDROID_HOME=/path/to/android-sdk   # platform 35 + build-tools 35.0.0
bash build.sh                               # الناتج: dist/munhana-1.2.apk
```

ينشئ سكربت البناء مفتاح تطوير محليًا إن لم يوجد مفتاح.

**الترخيص:** [MIT](LICENSE).
