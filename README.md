# Solat

A small Android app for Malaysian prayer times and Qibla direction, styled after Nothing OS: monochrome, dot-matrix numerals, one red accent.

- **Waktu** – today's times for your JAKIM zone with a live countdown to the next prayer. Zone is detected from your location or picked by hand. Tables are cached per month, so it works offline once loaded.
- **Kiblat** – compass that points to the Kaaba, using your location and magnetic declination for true north.

Prayer data comes from JAKIM via [api.waktusolat.app](https://api.waktusolat.app), the same backend used by [Waktu Solat Malaysia](https://github.com/mptwaktusolat/app_waktu_solat_malaysia), which inspired this app.

## Build

Kotlin + Jetpack Compose, no Google Play Services, minSdk 26.

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Every push to `main` builds an APK in GitHub Actions (see the run's **Artifacts**). Pushing a `v*` tag also publishes it as a GitHub release.

## Fonts

[Doto](https://github.com/oliverlalan/Doto) and [Space Grotesk](https://github.com/floriankarsten/space-grotesk), both under the SIL Open Font License (see `licenses/`).
