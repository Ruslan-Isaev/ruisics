# RUIS ICS

An Android app for importing, exporting, and managing calendar events in the
ICS format, with modular holiday packs and a shift scheduler.

Written in Kotlin with Jetpack Compose. Licensed under the
[Apache License 2.0](LICENSE). No ads, analytics, or telemetry.

📖 [Русская версия](README.ru.md)

## Features

### ICS import and export

- **Import**: read events from `.ics` files and add them to a calendar of
  your choice.
- **Export**: write calendar events to an `.ics` file, compliant with
  RFC 5545 — text escaping, line folding, `VALUE=DATE` for all-day events.

### Event management

- **Search and filter**: find events by keyword and by date range; review the
  matching list and its count before acting.
- **Batch deletion**: events are deleted in small chunks so the system
  calendar provider is never overloaded. The operation runs without blocking
  the UI, shows progress and per-chunk error handling, and can be safely
  retried after a partial failure.
- **Full cleanup**: clear all events from a selected calendar after an
  explicit warning and confirmation. The calendar itself, the account, and
  events in other calendars are never touched.

### Holiday packs

- **Modular data packs**: packs are plain JSON modules in `assets`
  (`world`, `russia`, `united_states`, and a Western Christian
  `example_religious` pack). Adding a new pack requires no code changes.
- **Flexible date rules**:
  - fixed date (with an optional year for one-off holidays);
  - N-th weekday of a month (N = −1 for the last one);
  - offset relative to another holiday in the pack;
  - calculated dates (Gregorian Easter);
  - per-year exceptions that override the rule for a specific year.
- **Pack management**: enable or disable packs independently, use several
  packs at once, view the pack description, category, and available
  languages, browse its holidays by year.
- **Duplicate-free import**: every imported event carries a stable UID
  (`pack + holiday + year`), so re-importing the same pack does not create
  duplicates. Import is preceded by a count check and confirmation, and the
  result (including errors) is reported. Disabling a pack never removes
  already imported events.

### Shift scheduler

- **Ready-made templates**, stored as JSON data (no code changes needed to
  add more): 2/2, 1/3 (24-hour shifts), 3/3, day/night/off-day, 5/2.
- **Custom cycles**: build your own cycle from day types and save it as a
  personal template (saved in app settings — built-in files are never
  modified).
- **Schedule parameters**: start date, event title, per-day-type start and
  end times, and a generation period.
- **Overnight shifts**: an end time earlier than the start time means the
  shift crosses midnight.
- **Preview and import**: preview the generated schedule, export it to
  `.ics`, and import it into a calendar of your choice.

### ICS generator

- Create a recurring event with a title, description, location, time zone,
  recurrence rule (daily / weekly / monthly / yearly), and a generation
  period.
- Export writes a proper `RRULE`; importing into the calendar adds the
  expanded occurrences.

### Localization

- Languages are separate JSON files in `assets/locales`; adding a new one
  requires no code changes.
- English and Russian are bundled. The app auto-detects the system language,
  falls back to English, and shows `[key]` (with a log warning in debug
  builds) when a key is missing.
- Pluralization (CLDR rules built in for `ru`/`en`, `one`/`other` for other
  languages), named and positional parameter substitution, locale-aware
  date and number formatting.
- The interface updates immediately after switching languages; the choice is
  persisted.

### Themes

- Light, dark, and system theme; the choice is saved between launches.

### Support the developer

- A menu item opens https://ruiscode.ru/donate.html in an external browser.

### Home menu

- Functions are grouped by purpose — ICS, event management, holiday packs,
  shift scheduler, ICS generator, settings, about. Destructive actions are
  visually separated from regular ones.

## Screenshots

Screenshots live in `docs/screenshots/`. Replace the placeholders below with
real captures (about 360 px wide).

| Home | ICS import/export | Event management |
|:---:|:---:|:---:|
| ![Home](docs/screenshots/home.png) | ![ICS](docs/screenshots/ics.png) | ![Events](docs/screenshots/events.png) |

| Holiday packs | Pack details | Shift scheduler |
|:---:|:---:|:---:|
| ![Holidays](docs/screenshots/holidays.png) | ![Pack details](docs/screenshots/holiday_details.png) | ![Shifts](docs/screenshots/shifts.png) |

| ICS generator | Settings | About |
|:---:|:---:|:---:|
| ![Generator](docs/screenshots/generator.png) | ![Settings](docs/screenshots/settings.png) | ![About](docs/screenshots/about.png) |

## Build

Requirements: JDK 17+, Android SDK (compileSdk 35), Gradle 8.9 (the wrapper
is included in the repository).

```bash
./gradlew assembleDebug      # debug APK
./gradlew testDebugUnitTest  # unit tests (JVM, no emulator needed)
./gradlew assembleRelease    # release APK (needs signing, see below)
./gradlew bundleRelease      # release AAB
```

### Signing the release build

Create a keystore and a `keystore.properties` file in the project root (both
are excluded from git):

```properties
storeFile=keystore/release.keystore
storePassword=…
keyAlias=ruisics
keyPassword=…
```

Without `keystore.properties`, the release build is unsigned.

## Project structure

```
app/src/main/
├── assets/
│   ├── locales/            # Language modules: manifest.json + <code>.json
│   ├── holiday_packs/      # Holiday packs: manifest.json + <pack>/
│   │   └── <pack>/         #   pack.json, holidays.json, translations/
│   └── shift_templates/    # Shift templates: manifest.json + templates/*.json
├── java/ru/ruiscode/ics/
│   ├── core/               # JSON parser, UIDs, external links
│   ├── i18n/               # Localization (catalog, resolver, state)
│   ├── settings/           # Settings (SharedPreferences + interface for tests)
│   ├── holidays/           # Models, catalog, date computation, planner
│   ├── shifts/             # Models, template catalog, generator
│   ├── ics/                # Parser, ICS generator, series expansion
│   ├── calendar/           # CalendarContract, importer, batch deletion
│   └── ui/                 # Compose: theme, navigation, screens, components
└── test/java/ru/ruiscode/ics/  # Unit tests (JVM)
```

Business logic (localization, holidays, shifts, ICS, deletion) does not
depend on the Android framework and is covered by unit tests; interaction
with the system calendar is isolated in `calendar/`.

## Adding a new language

1. Create `app/src/main/assets/locales/<code>.json` following the example of
   `en.json`: a `meta` object (code, name, nativeName, direction) and a
   `strings` object.
   - A string value may be an object with `one`/`few`/`many`/`other` forms
     for pluralization (CLDR rules are built in for ru/en; `one`/`other` are
     used for other languages).
   - Substitutions: `{name}` (named parameters) and `{0}`, `{1}`…
     (positional); `{{` is a literal brace.
2. Register the file in `app/src/main/assets/locales/manifest.json`.

No code changes are needed. A missing key falls back to English and is then
displayed as `[key]` (debug builds log a warning).

## Adding a holiday pack

1. Create the directory `app/src/main/assets/holiday_packs/<id>/`:
   - `pack.json` — metadata (id, nameKey, descriptionKey, category, version,
     dataFile, translationsDirectory, availableLocales, source, license).
     The display name goes through a localization key, not a literal string;
   - `holidays.json` — an array of holidays with `dateRule` rules;
   - `translations/<code>.json` — translations of the pack's keys.
2. Add a `{ "id": …, "directory": … }` entry to
   `app/src/main/assets/holiday_packs/manifest.json`.

Date rules (`dateRule.type`):

| type | fields | meaning |
|---|---|---|
| `fixed` | month, day, [year] | fixed date; with `year` — a one-off holiday |
| `nth_weekday` | month, weekday (1=Mon…7=Sun), n | N-th weekday of the month; n=-1 — the last one |
| `offset` | relativeTo, offsetDays | offset from another holiday in the pack |
| `calculated` | calendar: `gregorian_easter` | computed date |
| `skip` | — | skip (used in per-year `exceptions`) |

`exceptions`: a `{ "<year>": <dateRule> }` object that overrides the rule for
a specific year. Unknown algorithms are never approximated — the holiday is
flagged with an error in the UI.

State data sources and licenses honestly; if they are unknown, leave them
`null` and add a note that verification is needed.

## Adding a shift template

1. Create `app/src/main/assets/shift_templates/templates/<id>.json`:
   - `id`, `nameKey`, `descriptionKey`;
   - `cycle` — an array of day type ids (e.g.
     `["work", "work", "rest", "rest"]`);
   - `dayTypes` — an object: each day type has a `nameKey` and
     `startTime`/`endTime` in `HH:MM` format (both `null` — a day without an
     event; an end time not later than the start time means the shift
     crosses midnight);
   - `translations` — translations of the template's keys per language.
2. Add `{ "fileName": "<id>.json" }` to
   `app/src/main/assets/shift_templates/manifest.json`.

Custom cycles are stored separately — in the app settings (built-in files are
never modified).

## Holiday data

The pack dates were collected manually from publicly known official data and
computed algorithmically (Easter). The exact sources and licenses need
verification before redistribution — this is explicitly noted in the packs.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Limitations

- Recurring events (RRULE) are not expanded when importing `.ics`; when
  exporting a calendar, series are written out as individual occurrences.
- Deleting a series deletes it entirely (a calendar provider limitation) —
  the UI warns about this.
- Instrumented tests for operations on the real system calendar are not
  included (they require an emulator/device); the operation logic is covered
  by unit tests.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
