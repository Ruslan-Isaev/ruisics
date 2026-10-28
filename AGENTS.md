# Инструкции для агентов

## Проект

RUIS ICS — Android-приложение на Kotlin и Jetpack Compose для работы с
календарями ICS. Основной код находится в `app/src/main/java/ru/ruiscode/ics/`,
ресурсы и данные — в `app/src/main/assets/`.

## Правила изменений

- Сохраняйте существующий язык и стиль окружающего кода. Не удаляйте и не
  перезаписывайте незакоммиченные изменения пользователя.
- UI строится на Jetpack Compose. Логику дат, импорта, форматов ICS и
  локализации по возможности держите в соответствующих пакетах, отдельно от UI.
- Пользовательские строки интерфейса храните в JSON-модулях
  `app/src/main/assets/locales/`; не добавляйте их напрямую в Compose-код.
- При подстановке параметров используйте именованные пары (`"name" to value`)
  или `mapOf(...)` для `StringResolver.format`. Позиционные параметры передавайте
  отдельными значениями. Для множественных форм используйте `plural`.
- При добавлении языка обновите `locales/manifest.json` и обеспечьте наличие
  переводов ключей либо английского резервного перевода.
- Наборы праздников и шаблоны смен добавляйте как данные в `assets`, сохраняя
  формат и структуру соседних модулей.
- Не включайте секреты подписи (`keystore.properties`, keystore) в изменения.

## Версия приложения

Версия, показываемая в интерфейсе, жёстко задана в строках локализации
(`about.version`) — не выводите её через `BuildConfig`. При смене версии
обновите все три места одновременно:

1. `app/build.gradle.kts` — `versionCode` и `versionName` в `defaultConfig`;
2. `app/src/main/assets/locales/ru.json` — ключ `about.version`;
3. `app/src/main/assets/locales/en.json` — ключ `about.version`.

## Сборка

Требуются JDK 17 и Android SDK с платформой 35. Используйте Gradle Wrapper:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```

Release подписывается при наличии локального `keystore.properties`; без него
сборки release создаются без подписи. Основные исходящие файлы сборки Gradle:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

Юнит-тесты находятся в `app/src/test/` и запускаются командой
`./gradlew testDebugUnitTest`.
