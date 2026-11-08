# Дванадцятка — Android

> Оцінки, кишенькові й розклад для всієї сім’ї. ID у Google Play: `com.gifwarno.dvanadtsiatka`
> (після першої публікації не змінюється). Внутрішні пакети коду лишаються `com.gifwarno.rozklad.*` — користувачі їх не бачать.

Нативний застосунок (Kotlin + Jetpack Compose) до того самого бекенду Supabase, що й веб-версія в корені репозиторію.
Логіка нагород і розкладу перенесена 1:1, тому веб і Android рахують однаково (ті самі тести).

## Стек

| Що | Чим |
|---|---|
| Мова / UI | Kotlin 2.4, Jetpack Compose, Material 3, адаптивна навігація (телефон → нижня панель, планшет → бічна) |
| Архітектура | Чиста багатомодульна: `feature → core:data → core:network / core:database`, UDF (ViewModel + StateFlow) |
| DI | Hilt (KSP) |
| Навігація | Navigation 3 (типобезпечні `@Serializable` ключі) |
| Дані | Offline-first: Room — джерело правди, черга змін (outbox) + WorkManager для синхронізації |
| Бекенд | supabase-kt (Auth + PostgREST), Ktor/OkHttp |
| Налаштування | DataStore |
| Якість | ktlint (Spotless), Android Lint + Compose Lint, unit-тести (JUnit, Turbine), R8 у release, CI на GitHub Actions, Dependabot |

## Модулі

```
app                    ← точка входу: Application, MainActivity, навігація, тема
feature/
  auth                 ← вхід, реєстрація, створення/вступ до сім'ї
  today                ← «що зараз»: таймер уроку, розклад, рюкзак
  grades               ← оцінки, баланс, рівень, серії, бонуси/виплати
  homework             ← домашка з автотерміном «до наступного уроку»
  profile              ← сім'я, коди запрошення, тема, вихід
  schedule             ← редактор розкладу дитини: уроки, дзвінки, канікули
core/
  model                ← чисті моделі (Kotlin/JVM, без Android)
  domain               ← чиста логіка: нагороди, календар уроків (JVM, тести без емулятора)
  data                 ← репозиторії, офлайн-черга, синхронізація, ActiveFamily
  network              ← Supabase (DTO + RozkladNetworkDataSource)
  database             ← Room: сутності, DAO, схеми в core/database/schemas
  datastore            ← налаштування пристрою
  common               ← dispatchers, годинник, ticker
  designsystem         ← тема, кольори, базові компоненти
  ui                   ← спільні composable (бейджі предметів/оцінок, банер синхронізації)
  testing              ← фейки репозиторіїв, MainDispatcherRule, тестові дані
build-logic/convention ← convention-плагіни: однакові налаштування для всіх модулів
```

**Правила залежностей** (тримають код від «спагеті»):
- `feature:*` ніколи не залежить від іншого `feature:*`. Навігацію знає лише `:app`.
- `feature:*` бачить репозиторії (`core:data`), але не мережу й не БД напряму.
- `core:model` і `core:domain` — чистий Kotlin без Android: логіку можна перевірити за секунди.
- Версії бібліотек — тільки в `gradle/libs.versions.toml`; налаштування модулів — тільки в `build-logic`.

## Як працюють дані

1. Екран читає `ActiveFamily.context` (Room → Flow) і працює однаково з мережею та без неї.
2. Зміна (оцінка, домашка…) в одній транзакції записується в Room і в таблицю `outbox`. Тому її одразу видно в UI.
3. `SyncManager` відправляє чергу по порядку. Вставки йдуть з клієнтським UUID (`upsert … ignoreDuplicates`), тож повтор безпечний.
4. Немає мережі → `SyncWorker` (WorkManager) дочекається з'єднання, навіть якщо застосунок закрили.
5. При поверненні в застосунок `refresh()` спершу відправляє чергу, потім бере свіжий знімок сервера й накладає зміни, які ще не відправлено.

Захист даних забезпечує RLS у Supabase (ті самі політики, що й для вебу). Клієнт має лише publishable-ключ.

## Розклад дитини

Кожна дитина має власний розклад у таблиці `school_timetables` (міграція `supabase/migrations/20261108_school_timetables.sql`):
дзвінки, уроки на 5–7 днів і канікули. Редагують батьки або сама дитина, а сервер перевіряє формат
(`school_timetable_valid`). Збереження йде через ту саму офлайн-чергу: можна редагувати без інтернету.
Поки розклад не заповнено, «Сьогодні» пропонує його заповнити, а домашка ставить термін «на завтра».

## Розробка

Потрібні JDK 17+ та Android SDK (Android Studio сама все налаштує).

```bash
cd android
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew test                 # усі unit-тести
./gradlew spotlessApply        # автоформатування
./gradlew :app:lintRelease     # Android Lint
./gradlew :app:assembleRelease # release з R8
```

Звіти стабільності Compose: `./gradlew :app:assembleRelease -PcomposeReports=true`.

### Підпис release

Створи `android/keystore.properties` (він у `.gitignore`):

```properties
storeFile=../rozklad-release.jks
storePassword=…
keyAlias=rozklad
keyPassword=…
```

Без цього файлу release збирається непідписаним. Для Google Play використовуй Play App Signing.

## Безпека (чекліст для аудиту)

- Лише HTTPS (`network_security_config`), без cleartext.
- `allowBackup=false` + `data_extraction_rules`: сесія й дані дітей не потрапляють у хмарні бекапи.
- Єдина exported-активність — лаунчер.
- R8 + shrinkResources у release, `mapping.txt` зберігається як артефакт CI.
- Gradle wrapper зафіксовано за SHA-256, у CI додатково перевіряється `gradle-wrapper.jar`.
- Секрети підпису поза git; у `gradle.properties` лише публічні параметри Supabase.
- Права доступу до даних перевіряє сервер (RLS), а не клієнт.

## Що далі

- [ ] Віджет на головному екрані (Glance): поточний урок і таймер.
- [ ] Сповіщення: FCM для оцінок і нагадування про домашку (зараз це web-push).
- [ ] Повітряні тривоги: статус по області та режим «урок в укритті».
- [x] Свій розклад для кожної дитини (таблиця `school_timetables`, редактор у застосунку).
- [x] Веб-версія показує розклад дитини з `school_timetables`.
- [ ] Редактор розкладу у веб-версії (зараз — лише в Android).
- [ ] Вхід через Google (Credential Manager).
- [ ] Realtime-оновлення, поки застосунок відкритий.
- [ ] Baseline Profile для швидкого старту; скріншот-тести.
