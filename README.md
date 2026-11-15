# AllTestsAreUs — Android (Kotlin/Compose) + Docker Web Service + Jenkins CI

Система микро-уроков и тестирования с **долговременным запоминанием (Spaced Repetition SM-2)**.
На старте Android-приложение пустое — пользователь выбирает и скачивает нужные курсы с Docker-сервиса тестов (порт `8085`) или с любого другого сайта/HTML-страницы.

---

## Быстрый старт

### 1. Сервис тестов в Docker
- [`Dockerfile.web`](Dockerfile.web) + [`docker/nginx.web.conf`](docker/nginx.web.conf) — сборка с автогенерацией MP3-озвучки (`espeak-ng` + `lame`) и образ `nginx:1.27-alpine` с раздачей [`web-catalog/catalog.json`](web-catalog/catalog.json), [`web-catalog/index.html`](web-catalog/index.html), SVG-иконок, MP3-аудио и JSON-курсов без кэширования `catalog.json`.
- [`docker-compose.web.yml`](docker-compose.web.yml) — ручной запуск контейнера на порту `8085`:
  ```bash
  docker compose -f docker-compose.web.yml up -d --build
  ```

### 2. Сборка всего проекта в Docker через Jenkins
- [`Dockerfile.android`](Dockerfile.android) — Docker-образ сборщика (Java 17, Python 3, `platforms;android-34`, `build-tools;36.0.0`, нативный ARM64 `aapt`/`aapt2`/`zipalign`, Gradle 8.11.1).
- [`Jenkinsfile`](Jenkinsfile) — полностью выполняет тесты, проверку курсов, сборку Android APK/AAB (4 Product Flavors: `allInOne`, `orienteering`, `reptiles`, `languages`) и сборку/деплой Docker-сервиса тестов:
  1. Проверка JSON-курсов и unit-тесты внутри Docker (`alltestsareus-android-builder`).
  2. Сборка и push Docker-образа сервиса тестов (`alltestsareus-web` в локальный Docker Registry) и запуск контейнера на целевом веб-сервере (порт `8085`).
  3. Сборка Android APK/AAB в Docker (`-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2`, `compileSdk = 34`, подпись OpenBao или fallback [`app/release.keystore`](app/release.keystore)) и публикация APK на сервер раздачи.

### 3. Валидация курсов
```bash
python3 tools/validate_courses.py
```
Подробное описание архитектуры и модели БД: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
