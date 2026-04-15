# WikiLive

WikiLive — это вики-платформа с AI-ассистентом для работы с документацией. Проект состоит из React-фронтенда и Kotlin/Ktor бэкенда, работающего поверх PostgreSQL.

## Архитектура

```
WikiLive/
├── WikiLive.Api/          # Kotlin/Ktor backend
│   ├── db/                # Exposed DSL: таблицы, фабрика подключений
│   ├── models/            # @Serializable data classes
│   ├── routes/            # Ktor Routing: Pages, Comments, MwsProxy
│   └── resources/         # application.conf, logback, openapi
├── WikiLive.Client/       # React + Vite frontend
├── docker-compose.yml     # оркестрация API + PostgreSQL
└── README.md              # вы здесь
```

### Стек бэкенда

- **Ktor 3.0.3** — асинхронный HTTP-сервер и клиент
- **Exposed 0.57.0 (DSL)** — типобезопасный SQL over JDBC
- **PostgreSQL 16** — реляционная БД с `jsonb` для гибкого контента
- **HikariCP** — пул соединений
- **kotlinx.serialization** — сериализация JSON без reflection
- **Testcontainers** — интеграционные тесты с реальной PostgreSQL
- **Gradle (Kotlin DSL)** — сборка и управление зависимостями

### Схема данных

- **pages** — основные страницы вики
- **page_versions** — история изменений страниц (`CASCADE` на удаление страницы)
- **comments** — комментарии к страницам (`CASCADE` на удаление страницы)

Кастомный тип `jsonb` реализован через `JsonBColumnType` для корректной работы с PostgreSQL `jsonb` из Exposed DSL.

## API

Базовый префикс: `/api`.

### Pages

| Method | Endpoint | Описание |
|--------|----------|----------|
| GET | `/api/pages` | Список страниц |
| GET | `/api/pages/{id}` | Страница с комментариями и версиями |
| POST | `/api/pages` | Создать страницу |
| PUT | `/api/pages/{id}` | Обновить страницу (создаёт версию) |
| DELETE | `/api/pages/{id}` | Удалить страницу |
| POST | `/api/pages/{id}/restore/{versionId}` | Откат к версии |

### Comments

| Method | Endpoint | Описание |
|--------|----------|----------|
| POST | `/api/comments` | Добавить комментарий |
| POST | `/api/comments/{id}/like` | Лайкнуть комментарий |
| PUT | `/api/comments/{id}/resolve` | Отметить решённым |
| DELETE | `/api/comments/{id}` | Удалить комментарий |

### MwsProxy

Проксирование запросов к Fusion API (`https://fusion.mws.ru`). При отсутствии `Authorization` используется fallback-токен.

| Method | Endpoint | Описание |
|--------|----------|----------|
| GET | `/api/mws-proxy/spaces` | Список пространств |
| GET | `/api/mws-proxy/spaces/{spaceId}/nodes` | Узлы пространства |
| GET | `/api/mws-proxy/datasheets/{dstId}/fields` | Поля datasheet |
| GET | `/api/mws-proxy/datasheets/{dstId}/records` | Записи datasheet |
| PATCH | `/api/mws-proxy/datasheets/{dstId}/records` | Обновить записи |

## Запуск

### Требования

- JDK 21+
- Docker (для Docker Compose и интеграционных тестов)

### Dev-режим (порт 5132)

```bash
cd WikiLive.Api
./gradlew run
```

Фронтенд ожидает API на `localhost:5132`.

### Docker Compose

```bash
docker compose up --build -d
```

- API: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

## Конфигурация

Управляется через `WikiLive.Api/src/main/resources/application.conf` или переменные окружения:

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `PORT` | `5132` | Порт сервера |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wiki` | JDBC URL |
| `DB_USER` | `postgres` | Пользователь БД |
| `DB_PASSWORD` | `06` | Пароль БД |

## Тестирование

```bash
cd WikiLive.Api
./gradlew test
```

Тесты поднимают PostgreSQL в Testcontainers, запускают Ktor Test Host и проверяют полный жизненный цикл страниц, комментариев и проксирования.

## Сборка

```bash
cd WikiLive.Api
./gradlew build
```

Результат: `WikiLive.Api/build/libs/WikiLive.Api-all.jar` (fat JAR).

## Разработка с помощью ИИ

Этот бэкенд был разработан с использованием AI-ассистента (Sisyphus / OhMyOpenCode) в режиме pair-programming:

1. **Архитектурный выбор** — AI проанализировал legacy C# код и фронтенд, после чего предложил стек Ktor + Exposed DSL вместо Spring Boot/JPA, что дало меньше boilerplate и более предсказуемый SQL.
2. **Параллельная имплементация** — разработка велась волнами: сначала скелет и БД, затем роуты параллельно, затем тесты и Docker. Каждая волна верифицировалась перед следующей.
3. **Решение проблем совместимости** — AI самостоятельно выявил и обошёл баг компиляции Kotlin 2.0.21 с Java 26, переключив сборку на JDK 21. Также был найден workaround для `deleteWhere` в Exposed 0.57.0, несовместимого с текущей версией Kotlin.
4. **Контроль качества** — финальная проверка включала аудит плана, code review, анализ scope fidelity и проверку сборки. AI отслеживал соответствие между legacy HTTP-контрактом и новой реализацией (порт 5132, пропуск null в JSON).

Таким образом, нейросеть выполняла роль senior-разработчика: от выбора библиотек до интеграционных тестов, а человек — роль product owner и ревьюера.
