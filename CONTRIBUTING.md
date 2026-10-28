# Contributing to RUIS ICS

Thanks for your interest in the project! A few simple rules.

## How to propose changes

1. Create an issue describing the problem or suggestion.
2. Work in a separate branch off `main`.
3. Follow the existing code style (Kotlin, official code style).
4. For new features, add unit tests (see `app/src/test/`).
5. Make sure `./gradlew testDebugUnitTest assembleDebug` passes.
6. Describe your changes in the PR: what was done, why, and how it was verified.

## Code requirements

- Business logic must not depend on the Android framework, so that it can be tested on the JVM (see the package separation in the README).
- All UI strings must go through the localization system: add a key to `assets/locales/en.json` and `assets/locales/ru.json` (en is the fallback).
- New data (languages, holiday packs, shift templates) must be added only via JSON files following the schemas in the README, without changing Kotlin code.
- Calendar operations require explicit user confirmation.
- Do not add dependencies unless necessary; for each one, check the license and compatibility with F-Droid (builds from source only).

## Data licenses

- State the sources and licenses of holiday packs honestly. If a data license is undetermined, explicitly mark it as such.
- Do not commit secrets, signing keys, local IDE settings, or private calendar data to the repository.

## License

The project is distributed under the Apache License 2.0. By submitting changes, you agree to their distribution under these terms.