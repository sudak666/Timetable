# Дванадцятка — Android

> Оцінки, кишенькові й розклад для всієї сім’ї. ID у Google Play: `ua.dvanadtsiatka`
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
  widget               ← віджет «Розклад зараз» (Glance) з оновленням на межах уроків
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

### Демо-режим (лише debug)

Вигадана сім'я (шестикласник Тарас і мама Оксана) з оцінками, домашкою, челенджами й розкладом.
Усе в пам'яті, жодного запиту на сервер, тож можна натискати будь-що. Демо-режим потрібен для огляду екранів
і скриншотів магазину. У release-збірки він не потрапляє: код лежить в `app/src/debug/.../demo`.

```bash
# увімкнути (role=child або parent; time — котра зараз година в демо)
adb shell "run-as ua.dvanadtsiatka.debug sh -c 'printf \"role=child\ntime=10:40\n\" > files/demo'"
adb shell am force-stop ua.dvanadtsiatka.debug
# вимкнути
adb shell run-as ua.dvanadtsiatka.debug rm files/demo
```

Поки демо-режим увімкнений, debug-застосунок не бачить справжнього акаунта. Тому вмикайте його на емуляторі,
а не на телефоні, де ви увійшли в справжній акаунт.

Скриншоти магазину лежать у `fastlane/metadata/android/uk-UA/images/phoneScreenshots/` (1080×1920). Їх знято
в демо-режимі на емуляторі Pixel з годинником статус-бару 10:40, а підписи й рамки додано скриптом `fastlane/store_screenshots.py <знімки> <куди> core/designsystem/src/main/res/font` (потрібен Pillow).

## Push-сповіщення (Firebase)

Сервер (`supabase/functions/school-push`) надсилає ті самі події і в веб-push, і в Android (FCM HTTP v1, `fcm.ts`).
Токени пристроїв — таблиця `school_fcm_tokens` (міграція `20261109_school_fcm_tokens.sql`). Користувач сам вмикає
сповіщення в «Профілі»; при вимкненні або виході з акаунта токен видаляється. Без налаштувань Firebase усе тихо вимкнено.

Як увімкнути (одноразово):
1. [console.firebase.google.com](https://console.firebase.google.com) → новий проєкт (Google Analytics не потрібна).
2. Додай два Android-застосунки: `ua.dvanadtsiatka` і `ua.dvanadtsiatka.debug`.
3. З `google-services.json` (файл у git не додавати) перенеси значення в `android/gradle.properties`:
   ```properties
   rozklad.firebase.projectId=…          # project_info.project_id
   rozklad.firebase.senderId=…           # project_info.project_number
   rozklad.firebase.apiKey=…             # client[].api_key[].current_key
   rozklad.firebase.appId=…              # mobilesdk_app_id для ua.dvanadtsiatka
   rozklad.firebase.appIdDebug=…         # mobilesdk_app_id для …debug
   ```
   Ці значення публічні (вони є в будь-якому APK), секрету тут немає.
4. Firebase → Project settings → Service accounts → Generate new private key. Вміст JSON (це **секрет**!) —
   у Supabase → Edge Functions → Secrets як `FCM_SERVICE_ACCOUNT`. Нікуди більше його не копіюй.

## Адреси для Google Play Console

- Політика конфіденційності: https://sudak666.github.io/Timetable/privacy.html (`public/privacy.html`)
- Видалення акаунта: https://sudak666.github.io/Timetable/ → увійти → «Я» → «Видалити акаунт»

## Видалення акаунта (вимога Google Play)

«Профіль» → «Видалити акаунт», у застосунку й у веб-версії (адресу сайту вказати в Play Console як URL для видалення).
Сервер (`school_delete_account()`, міграція `20261108_school_delete_account.sql`) видаляє акаунт і всі дані, де користувач — дитина.
Останній з батьків видаляє всю сім'ю; якщо є другий з батьків, сім'я лишається йому. Перед підтвердженням
показуються наслідки, і треба ввести слово «ВИДАЛИТИ». Локальні дані стираються лише після підтвердження сервера.

## Безпека (чекліст для аудиту)

- Лише HTTPS (`network_security_config`), без cleartext.
- `allowBackup=false` + `data_extraction_rules`: сесія й дані дітей не потрапляють у хмарні бекапи.
- Єдина exported-активність — лаунчер.
- R8 + shrinkResources у release, `mapping.txt` зберігається як артефакт CI.
- Gradle wrapper зафіксовано за SHA-256, у CI додатково перевіряється `gradle-wrapper.jar`.
- Секрети підпису поза git; у `gradle.properties` лише публічні параметри Supabase.
- Права доступу до даних перевіряє сервер (RLS), а не клієнт.

## Що далі

- [x] Віджет на головному екрані (Glance): поточний урок, перерва, уроки дня; оновлюється на межах уроків.
- [x] Push-сповіщення в Android (FCM): оцінки, підтвердження, бонуси, челенджі, домашка на завтра.
- [ ] Повітряні тривоги: статус по області та режим «урок в укритті».
- [x] Свій розклад для кожної дитини (таблиця `school_timetables`, редактор у застосунку).
- [x] Веб-версія показує розклад дитини з `school_timetables`.
- [ ] Редактор розкладу у веб-версії (зараз — лише в Android).
- [ ] Вхід через Google (Credential Manager).
- [ ] Realtime-оновлення, поки застосунок відкритий.
- [ ] Baseline Profile для швидкого старту; скріншот-тести.
