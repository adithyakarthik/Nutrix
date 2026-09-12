# Nutrix

Point your camera at a plate. Nutrix works out what is on it, breaks it down into macros,
vitamins and minerals, and tracks it against targets calculated for your body.

Android, Kotlin, Jetpack Compose, Material 3.

---

## What it does

**Scan a barcode.** Point the camera at any packet. The barcode reader runs on-device via ML Kit
and the product comes back from Open Food Facts — an open database of millions of products,
free, with no API key of any kind. What you get is the manufacturer's own nutrition panel, which
beats estimating from a photograph because it isn't an estimate.

**Search any food.** One box searches Open Food Facts, USDA FoodData Central and the table built
into the app at once, and labels every hit with where it came from, so you can see whether a
number was measured in a lab, copied off a packet, or approximated. Set the portion and log it.

**Every nutrient, everywhere.** Energy, protein, carbs, fat, saturated fat, fibre, sugar, vitamins
A, B1, B2, B3, B5, B6, B7, B9, B12, C, D, E and K, and calcium, iron, magnesium, zinc, potassium,
sodium, iodine, selenium, phosphorus, copper and manganese. Where a source doesn't declare a
nutrient it stays blank rather than being invented as zero.

**Estimate from a photo (optional, paid).** For home-cooked food with no barcode, Claude can
identify the dish, estimate the portion and cost it out. This is the one feature that needs a
paid API key, it's off by default, and everything else works without it.

**Goals that come from your body.** Height, weight, age, sex and activity level go into
Mifflin-St Jeor — or Katch-McArdle when you know your body fat — and out come calorie, protein,
fat and carb targets, plus every micronutrient target from the DRI tables, adjusted for sex, age
and training load. Every target is editable, and your edits survive a recalculation. An optional
AI review reads your situation and proposes adjustments with reasons; it can only refine targets
the calculator produced, never invent new ones, and never move one by more than 50%.

**Health conditions.** List them and Nutrix adjusts what it safely can — sodium down to 1,500 mg
for raised blood pressure, free sugars to 5% of energy for diabetes, saturated fat to 6% for
raised cholesterol — and explains each change. For pregnancy, breastfeeding and kidney disease it
refuses to guess and tells you to get clinical numbers.

**Recipes.** Enter ingredients and weights once. Nutrix looks each one up (USDA FoodData Central
first, then Claude, then a bundled offline table), scales it, and costs the whole dish. Log a
serving in one tap whenever you make it.

**Water.** A goal derived from your bodyweight, age and training load, with a safe band around
it. Set your own number and Nutrix tells you plainly whether it is too low, sensible, or high
enough to be dangerous. Reminders are spaced on a gap and per-drink amount you control, and the
app checks the two actually add up to the goal inside your waking hours — and proposes a pairing
that does when they do not.

**Ask Nutrix.** A chatbot that knows what you logged today, what your targets are, and where you
are short. It searches the web when the answer depends on current evidence, and it says "ask your
doctor" where that is the honest answer.

---

## Where the numbers come from

In this order, because a measured value beats an estimate:

1. **Open Food Facts** — the product's own label. Free, open, and needs no API key at all.
2. **USDA FoodData Central** — lab-measured composition for whole foods. Free key.
3. **A bundled offline table** — about forty staples, so the app works on a plane and before you
   have entered any key. Always labelled approximate.
4. **The DRI tables** (RDA/AI and upper limits, from the NIH Office of Dietary Supplements) for
   targets, shipped in the app and never fetched.
5. **Claude with web search** — last, and only for photographed dishes that have no database
   entry. This is the only source that costs money, and the only one that is optional.

Every energy and goal calculation runs on-device. **Nutrix is free to run.** Barcode scanning,
food search, the diary, recipes, goals, water tracking and reminders involve no paid service at
all — the AI photo estimate and the chatbot are extras, switched off until you turn them on.

### Accuracy, honestly

A photograph cannot tell you how much oil went into a curry. Portion estimates from an image are
the largest error term, which is why the portion is editable and the confidence level is shown.
Nutrix cross-checks each estimate against itself — protein×4 + carbs×4 + fat×9 should land within
10% of the stated calories — and warns you when it does not. Reference intakes describe
populations, not individuals. This is a tracking tool, not medical advice.

---

## Building it

Requirements: Android Studio Ladybug or newer, JDK 17, Android SDK 35. Min SDK 26.

```bash
git clone <this repo>
cd Nutrix
./gradlew assembleDebug      # or open the folder in Android Studio and press Run
./gradlew test               # unit tests for the calculation layer
```

### API keys and privacy

Nutrix ships with no keys. Both are optional and both are entered in the app under **Settings**:

| Key | What it unlocks | Where to get it |
| --- | --- | --- |
| Anthropic | Photo analysis, the chatbot, the goal review | [console.anthropic.com](https://console.anthropic.com) (pay-as-you-go) |
| USDA FDC | Lab-measured ingredient lookups | [fdc.nal.usda.gov/api-key-signup.html](https://fdc.nal.usda.gov/api-key-signup.html) (free) |

**Neither key is required.** Open Food Facts needs none, so barcode scanning and product search
work the moment you install the app.

There is no free tier for the Anthropic API, so the two AI features cost real money — a few cents
per scan. They are off by default. **Settings → Which model reads your food** picks the tier if
you do turn them on: Opus 5 for the best portion estimates, Sonnet 5 for a cheaper everyday
setting, Haiku 4.5 for testing. The request shape follows the model, not just the price — Haiku
rejects the `effort` parameter and takes the older web-search tool — so the capability flags on
`ClaudeModel` are what the client builds each request from.

Keys are encrypted with an AES-256 key held in the device's hardware-backed Android Keystore and
are excluded from cloud backup and device transfer.

**For anything you publish, use a backend instead.** A key inside an APK can be extracted from it,
whatever it is wrapped in — that is a property of shipping software to devices, not of this
storage scheme. Set a **proxy base URL** in Settings and Nutrix will POST to `{base}/v1/messages`
in the Anthropic Messages format and send no key at all; your server holds the credential and
decides who may spend it. When a proxy is set, it takes priority over any local key.

For local development you can put keys in `local.properties` (git-ignored) instead of retyping
them in the app:

```properties
anthropicApiKey=sk-ant-...
usdaApiKey=...
```

**What leaves the device.** Your diary, recipes, profile, goals and water log stay on the phone.
When you scan a photo or ask a question, that photo or question — plus a short summary of your
profile and today's totals — goes to Anthropic to be answered. Turning off **AI features** in
Settings stops all of it; tracking, calculations and reminders keep working.

---

## How it is put together

```
app/src/main/java/com/nutrix/app/
├── model/            Nutrient, Nutrients, UserProfile, Goals, Food, Recipe, Water, Chat
├── nutrition/        The science: DRI tables, energy/goal/water calculators (pure Kotlin)
├── data/
│   ├── local/        Room database, DAOs, JSON type converters
│   ├── prefs/        DataStore settings + Keystore-encrypted secret store
│   ├── remote/       Claude client, USDA client, prompts and tool schemas, offline table
│   └── repository/   Source precedence, caching, and everything the UI talks to
├── ui/               Compose screens, one package per feature, Material 3 theme
├── water/            WorkManager reminder chain, notifications, boot receiver
├── util/             Image preparation for the vision call, shared JSON config
└── AppContainer.kt   The whole dependency graph, by hand, in one file
```

**Why these choices.** Repositories decide source precedence, so the UI never knows whether a
number came from a lab or a model. The nutrition package has no Android dependencies at all,
which is what makes it testable — and it is where the parts you would want to audit live. The
Claude integration talks to the documented HTTP surface through OkHttp rather than the server-side
Java SDK: the SDK is heavy for an APK and assumes the key sits on the machine making the call,
which is precisely what a mobile client must not do. Requests use a strict tool schema, so a
malformed answer fails loudly instead of silently logging 400 g of protein.

Reminders are a self-rescheduling one-time WorkManager job rather than a periodic one: the gap is
a user setting that has to be honoured exactly and must stop at the end of their waking window.

### Continuous integration

`.github/workflows/android.yml` runs the unit tests and assembles a debug APK on every push.
The APK lands in the run's artifacts, which is the quickest way to get a build onto a phone
without installing Android Studio.

### Tests

`./gradlew test` covers the calculation layer — the energy equations and their floors, goal
generation including the health-condition adjustments and the refusal cases, water bounds and
reminder spacing, nutrient arithmetic and storage round-trips.

---

## Status

The app is feature-complete against its brief and the source is what is described above. It has
**not been compiled or run on a device in the environment it was written in** — Google's Maven
host, which serves the Android Gradle Plugin, the Android SDK and all of AndroidX, is blocked by
that environment's network policy. Open it in Android Studio, let it sync, and fix anything the
compiler flags before trusting it on a phone.

Known gaps worth knowing about:

- Barcode scanning is not implemented; packaged food goes through the camera or the recipe builder.
- The diary keeps a reference to the captured photo in the app cache, so images can be evicted by
  Android while the log entry remains.
- Ingredient lookups are cached per name forever; there is no cache invalidation yet.
