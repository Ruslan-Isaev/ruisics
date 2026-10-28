# Third-Party Components and Data

## Libraries (included in the APK)

| Component | Version | License |
|---|---|---|
| Kotlin stdlib (org.jetbrains.kotlin) | 2.0.21 | Apache License 2.0 |
| androidx.core:core-ktx | 1.15.0 | Apache License 2.0 |
| androidx.activity:activity-compose | 1.9.3 | Apache License 2.0 |
| androidx.compose (ui, graphics, material3; BOM) | 2024.12.01 | Apache License 2.0 |

The Apache 2.0 license texts: https://www.apache.org/licenses/LICENSE-2.0
(also included with the dependencies in the `META-INF/` directories of the AndroidX packages).

## Test-only libraries (not included in the APK)

| Component | Version | License |
|---|---|---|
| JUnit | 4.13.2 | Eclipse Public License 1.0 |

## Build tools (not included in the APK)

| Component | Version | License |
|---|---|---|
| Gradle | 8.9 | Apache License 2.0 |
| Android Gradle Plugin | 8.7.3 | Apache License 2.0 |

## Holiday packs

All packs were compiled manually from well-known official dates; computed
dates (Easter) are calculated using the Meeus/Jones/Butcher algorithm. **The sources and
licenses of the data have not been verified and must be clarified before
redistribution** — this is explicitly noted in the metadata of each pack (`pack.json`) (`source`/`license` fields without specific licenses given).

| Pack | Contents |
|---|---|
| `world` | International observances (fixed dates) |
| `russia` | Russian public holidays (fixed dates) |
| `united_states` | U.S. federal holidays (including nth-weekday-of-month rules) |
| `example_religious` | Western Christian holidays (Easter and offsets from it) |

## Other

- The app contains no analytics, advertising, telemetry, or network
  services; the only external link is the developer's support page
  https://ruiscode.ru/donate.html (opens in an external browser).