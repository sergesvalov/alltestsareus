# Архитектура платформы «AllTestsAreUs» (Android + Web Course Hub)

## 1. Концепция и решение по разделению тем

Платформа объединяет три совершенно разных направления (**Языки**, **Рептилии / Герпетология**, **Спортивное ориентирование**), используя единое ядро долговременного запоминания:
- **Микро-уроки (Теория)** $\rightarrow$ **Практика (Тесты разных форматов)** $\rightarrow$ **Интервальное повторение (Spaced Repetition SM-2)**.

### Почему один репозиторий + Product Flavors (вместо разных git-веток):
Разделение по git-веткам привело бы к дублированию 95% кода (база данных, парсер веб-страниц, экраны тестов, алгоритм памяти). Вместо этого в `app/build.gradle.kts` настроено измерение `topic` с **4 Product Flavors**:

| Flavor (`topic`) | Application ID | Назначение | Фильтр контента (`FLAVOR_CATEGORY`) |
| :--- | :--- | :--- | :--- |
| **`allInOne`** | `com.alltestsareus.app` | Универсальный хаб со всеми курсами и выбором категории | `"all"` (показывает все темы) |
| **`languages`** | `com.alltestsareus.app.languages` | Отдельное приложение «Языки и Лексика» | `"languages"` |
| **`reptiles`** | `com.alltestsareus.app.reptiles` | Отдельное приложение «Мир Рептилий» | `"reptiles"` |
| **`orienteering`** | `com.alltestsareus.app.orienteering` | Отдельное приложение «Спортивное Ориентирование» | `"orienteering"` |

Командой `./gradlew assembleDebug` (или выбором Build Variant в Android Studio) можно собрать как единый комбайн, так и 3 отдельных узкоспециализированных приложения для Google Play из одной кодовой базы.

---

## 2. Загрузка тестов с внешних веб-страниц (Web-to-Android Protocol)

В системе реализован модуль `WebCourseImporter`, который позволяет загружать новые курсы и тесты из интернета без обновления приложения в Google Play. Поддерживаются **3 формата веб-источников**:

### Способ 1: Веб-страница с встроенным JSON (`HTML Embedded Course`)
Вы можете опубликовать обычную HTML-страницу на любом хостинге, блоге или GitHub Pages. Человек может читать её в браузере и проходить тест на сайте, а Android-приложение при вводе URL этой страницы находит внутри тег:
```html
<script id="alltests-course-data" type="application/json">
  { "courseId": "...", "title": "...", "lessons": [ ... ] }
</script>
```
Парсер вырезает этот блок, валидирует структуру и сохраняет курс, уроки и карточки в локальную БД `Room` для офлайн-работы.

### Способ 2: Прямая ссылка на JSON-файл курса (`.json`)
Ссылка вида `https://example.com/courses/orienteering-isom.json` напрямую десериализуется в объекты курса.

### Способ 3: Каталог курсов (`catalog.json`)
Манифест со списком доступных курсов и номерами версий (`version`). Приложение сверяет локальные версии курсов в `Room` с сервером и подкачивает обновления.

---

## 3. Алгоритм долговременного запоминания (SRS / SM-2)

В отличие от обычных викторин («прошёл и забыл»), вопросы привязаны к **единицам знаний (`Concept`)**:
1. Например, `Concept` — это условный знак *«ISOM 307: Непроходимое болото»*, вид *«Пятнистый эублефар»* или фразовый глагол *«put up with»*.
2. К одному `Concept` привязано несколько проверочных вопросов (`SINGLE_CHOICE`, `WORD_ORDER`, `FLASHCARD`).
3. Таблица `user_concept_progress` хранит для каждого понятия:
   - `easinessFactor` (начальный `2.5`)
   - `intervalDays` (интервал в днях: `1` $\rightarrow$ `3` $\rightarrow$ `7` $\rightarrow$ `16` $\rightarrow$ `40`...)
   - `repetitions` (серия успешных ответов подряд)
   - `nextReviewEpochMs` (точная дата/время следующего показа)
4. На главном экране всегда доступна кнопка **«Умное повторение на сегодня»**, которая собирает все понятия с `nextReviewEpochMs <= now` (по всем урокам выбранного направления).

---

## 4. Структура репозитория

```text
alltestsareus/
├── docs/
│   └── ARCHITECTURE.md                  # Архитектурная документация
├── web-catalog/                         # Веб-платформа и источник тестов (готово для GitHub Pages)
│   ├── index.html                       # Интерактивный веб-портал (витрина, веб-плеер тестов, генератор HTML)
│   ├── standalone-sample-test.html      # Пример внешней веб-страницы с вшитым тестом для импорта по URL
│   ├── catalog.json                     # Манифест каталога курсов
│   └── courses/                         # JSON-пакеты курсов по 3 темам
│       ├── orienteering-isom.json
│       ├── reptiles-herpetology.json
│       └── languages-english.json
├── app/                                 # Android-приложение (Kotlin + Jetpack Compose + Room)
│   ├── build.gradle.kts                 # Настройка 4 Product Flavors и зависимостей
│   └── src/main/
│       ├── assets/courses/              # Предзагруженные офлайн-курсы
│       └── java/com/alltestsareus/app/
│           ├── model/                   # DTO для JSON/Web-парсинга
│           ├── data/local/              # Room Entities, DAO, Database
│           ├── data/remote/             # WebCourseImporter (парсер HTML-страниц и JSON по URL)
│           ├── data/repository/         # LearningRepository (Offline-first + синхронизация)
│           ├── domain/srs/              # SpacedRepetitionEngine (алгоритм SM-2)
│           └── ui/                      # Jetpack Compose экраны и ViewModel
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

