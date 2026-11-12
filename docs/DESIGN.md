# Тема и движение

`ui/Theme.kt` — единая точка настройки.

Палитра: HCT Material Color Utilities из зафиксированной Material Components 1.12.0. Используются публичные Java-классы, помеченные библиотекой RestrictedApi; это осознанная зависимость на фиксированную версию. При обновлении зависимости нужны проверки палитры. Primary chroma 36, secondary 16, tertiary hue+60/chroma24, neutral6, neutralVariant8. Тоны основных цветов 40/80, on 100/20, containers90/30 и onContainer10/90. Surface98/6, текст10/90. Ошибки сохраняют семантическую M3-палитру. Все surface/container/outline-роли адаптируются к seed. Генерация вне UI-потока, кэш по seed и светлому/тёмному режиму. Dynamic Color на Android 12+; на более старых — стандартная палитра. Ручной seed используется только при режиме «Свой цвет».

Motion: short120 мс, screen280 мс, palette400 мс, progress500 мс. Стандартные Material ripple и modal sheet. Fade-переходы Navigation, анимация контейнера/размера карточки и прогресса; общая интерполяция цветовых ролей темы без пересоздания Activity. Reduced motion использует120 мс. Системный animator duration scale учитывается Compose. Списки используют стабильные ключи. Данные и AlarmManager не зависят от анимаций.

Проверка производительности должна выполняться на физическом устройстве в release/profileable с FrameTiming/Macrobenchmark. Software emulator пригоден для функциональных проверок, не для подтверждения FPS. Цель ТЗ <5% jank на выбранном устройстве пока не подтверждена; гарантия 60/90/120 FPS не даётся.
