# Децентрализованная модель данных

## Реализовано

- Firebase Authentication Email/Password; два отдельно зарегистрированных UID.
- Локальный Room календарь v3 с явными миграциями v1→v2→v3.
- Разрешения: личное событие меняет только его автор, совместное — оба.
- Сопряжение через QR: случайный ID пары, 256-битный ключ AES и
  ручное подтверждение второго пользователя создателем.
- AES-256-GCM с уникальным nonce и привязкой AAD к pairId и writerUid.
- Два зашифрованных снимка в `pairs/{pairId}/snapshots/{uid}`.
- Серверные Security Rules с белым списком двух UID и проверкой
  роли автора снимка. Их надо опубликовать в Firebase Console.
- Фоновое обновление через WorkManager, обновления при открытом
  приложении через Firestore snapshot listener.
- Конфликтные изменения общего события сохраняются отдельно.
- Локальные данные сохраняются без интернет-соединения.

## Не реализовано

- Экспорт и восстановление ключей при потере обоих телефонов.
- Шардирование снимков, если их размер превысит 600 КБ.
- Поддержка более двух аккаунтов, дополнительных устройств,
  изменение состава пары и отзыв уже переданного ключа.
- Полный автоматический emulator-тест Firestore Rules и
  два физических устройства для end-to-end испытаний.

## Reaction wire format v2

The outer Firestore encrypted-envelope schema remains 1; the encrypted inner
calendar snapshot advances to version 2 with `heartChoice`. The decoder
accepts version 1 snapshots, deriving one heart from historical color counts,
and emits only version 2. Updated devices no longer increment historic counts.
Both phones should update to this version for consistent one-heart behavior.
