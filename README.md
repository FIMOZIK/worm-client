<div align="center">

<img src="src/main/resources/worm_logo.png" alt="worm launcher" width="180"/>

# worm launcher

**A modern, customizable Minecraft launcher with multi-loader support.**

[![Version](https://img.shields.io/badge/version-1.0.8-00008B?style=flat-square)](https://github.com/FIMOZIK/worm-client/releases)
[![License](https://img.shields.io/badge/license-MIT-FFA07A?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/java-21+-00008B?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/platform-Windows-FFA07A?style=flat-square&logo=windows)](https://github.com/FIMOZIK/worm-client/releases)

</div>

---

## ✨ Возможности

### 🎮 запуск игры
- 🎯 **Мультизагрузчик** — Vanilla, Fabric, Forge, Quilt из коробки
- 🎨 **OptiFine** — установка installer'а + запуск через `launchwrapper`, автокопирование из `%APPDATA%\.minecraft\`
- 📦 **Модпаки** — импорт `.mrpack` (Modrinth) и `.zip` (CurseForge), каждая сборка в своей папке `game/modpacks/{название}/`
- ☕ **Автоскачивание Java** — если нужной JRE нет, лаунчер сам скачает (Mojang → Adoptium fallback) в `runtime/{major}/`

### 🔐 аккаунты
- **три способа входа** — Ely.by (пароль), Ely.by (OAuth в браузере), Microsoft (device code)
- автологин после первого входа (сессия в `session.json`)
- 🖼 **Skin preview** — голова текущего скина в правом верхнем углу

### 🎨 кастомизация
- 🎨 **7 тем оформления** — WormLauncher, Cyberpunk, Blood, Aqua, Mono, Sunset, Mint + light/dark
- 🖼 **Кастомизация фона** — PNG/JPG/GIF/MP4 с настройкой прозрачности
- 🔊 **Звук нажатия клавиш** — свой WAV/MP3/MP4
- 🔤 **Свои шрифты** — TTF/OTF из файла
- 🌐 **Два языка** — Русский и English

### 📁 профили и память
- 📦 **Профили сборок** — сохраняй «1.21.11 + Fabric 0.20.1» под своим именем
- 💾 **Memory slider** — 1024–16384 MB, применяется ко всем запускам
- ⚙️ **Редактор профиля** — имя, своя память, custom JVM args (`-XX:+UseG1GC` и т.п.)
- 🎯 **Discord Rich Presence** — статус, голова скина

### 🔧 диагностика и стабильность
- 🐛 **CrashParser** — вместо сырого stacktrace показывается карточка с причиной:
  - `UnsupportedClassVersionError` → «поставь JDK N»
  - `EXCEPTION_ACCESS_VIOLATION` → «natives / GPU драйвер»
  - `OutOfMemoryError` → «мало памяти»
  - и т.д.
- 📄 **`worm-mc-latest.log`** — полный вывод MC (tail ловится при падении)
- 🌐 **3 зеркала manifest** — `piston-meta` → `launchermeta` → `bmclapi2` (fallback при таймауте)
- 🔁 **Retry на сетевых запросах** — 3 попытки с задержкой 1s/2s

### 🔄 обновление
- авто-проверка обновлений через GitHub Releases
- встроенное самообновление: **работает и для jar, и для exe** (`SelfUpdater` определяет режим)
- скачивание в `.jar.part` → переименование при успехе (не ломается при обрыве)

### 🗑 удаление
- через лаунчер — ⚙ Настройки → «Удалить лаунчер...»
- через отдельный exe — `WormUninstaller.exe`
- через аргумент — `WormLauncher.exe --uninstall`
- при удалении можно выбрать: удалять ли `game/` (миры, моды) или только лаунчер

---

## 📥 Установка

### Вариант 1 — готовый .exe (для новичков)

1. Скачай последний **`WormLauncher-1.0.8-full.zip`** из [Releases](https://github.com/FIMOZIK/worm-client/releases)
2. Распакуй в **`D:\WormLauncher\`** (⚠ не в OneDrive — синхронизация ломает автообновление)
3. Запусти `WormLauncher.exe`
4. При первом запуске выбери:
   - язык (RU / EN)
   - папку для лаунчера
   - папку для Minecraft
   - создать ли ярлык на рабочем столе

JRE вшита в exe — **Java ставить не надо**.

### Вариант 2 — из jar (если есть Java 21+)
java -jar worm-launcher-1.0.8.jar

Требуется **Java 21+**. если нужной JRE нет — лаунчер сам её скачает.

---

## 🚀 Быстрый старт

1. **Войди в аккаунт** — Ely.by (пароль/браузер) или Microsoft (device code)
2. **Выбери версию MC** — например `1.21.11`
3. **Выбери загрузчик** — Vanilla / Fabric / Forge / Quilt / OptiFine
4. **Подкрути память** (слайдер справа) — по умолчанию 4096 MB
5. **Play**

### модпак
- **«Импорт сборки»** → выбери `.mrpack` или `.zip` → лаунчер сам скачает моды
- профиль появится в списке → выбери → **Play**
- модпак играет в своей папке, миры не пересекаются с обычной игрой

### OptiFine
1. `loader = OptiFine`
2. выбери версию OptiFine
3. **Play** → откроется installer → нажми **Install** → закрой окно → MC запустится с OptiFine

⚠ **installer ищет MC только в `%APPDATA%\.minecraft\versions\`**. если для нужной версии там пусто:
- **A)** запусти эту версию один раз в официальном лаунчере
- **B)** скопируй `game\versions\X.Y.Z\` → `%APPDATA%\.minecraft\versions\X.Y.Z\`

инструкция также пишется в лог лаунчера.

---

## 🗑 Удаление

- через лаунчер — ⚙ Настройки → **«Удалить лаунчер...»**
- через отдельный exe — `WormUninstaller.exe`
- через аргумент — `WormLauncher.exe --uninstall`

При удалении можно выбрать: удалять ли файлы Minecraft (`game/` с мирами и модами) или только файлы лаунчера.

---

## 🎨 Темы

| WormLauncher | Cyberpunk | Blood |
|--------------|-----------|-------|
| Aqua | Mono | Sunset |
| Mint | | |

Все темы работают в двух режимах — **dark** и **light**. Активная тема и режим переключаются в ⚙ Настройках.

---

## 📁 Где что лежит
%USERPROFILE%.wormlauncher
install.json ← пути установки (НЕ сносится clean)

D:\wormlauncher
config.json ← настройки
session.json ← токен аккаунта
worm-mc-latest.log ← вывод MC

game
versions\ ← версии MC
libraries\ ← библиотеки
assets\ ← ресурсы
natives\ ← нативные DLL
modpacks\ ← модпаки (отдельные папки)
.cache\optifine\ ← кэш OptiFine

runtime{major}\ ← автоскачанные JRE
libs\authlib-injector.jar


---

## 🔧 Сборка из исходников

```bash
git clone https://github.com/FIMOZIK/worm-client.git
cd worm-client

# сборка jar
gradlew.bat build

# результат
# build/libs/worm-launcher-1.0.8.jar
Требования: JDK 21, Windows 10/11.

сборка .exe (опционально)
cmd
rmdir /s /q dist-input
mkdir dist-input
copy build\libs\worm-launcher-1.0.8.jar dist-input\

jpackage --type app-image --name WormLauncher --app-version 1.0.8 ^
  --input dist-input --main-jar worm-launcher-1.0.8.jar ^
  --main-class launcher.LauncherMain ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest dist
⚠ папка dist-input — только с нужным jar. если копировать build\libs целиком, туда попадёт testmaen-1.0.8.jar (jar-task от Gradle) и сломает сборку.

⚠ Известные ограничения
MS browser login — не поддерживается (нужен свой Azure tenant). используй device code flow (кнопка «MS»).

NeoForge — пока маппится на Forge-путь, может не работать на новых сборках.

мод-менеджер в UI — пока нет, моды руками в game/modpacks/{id}/mods/.

скины Ely в OptiFine — не работают без CustomSkinLoader (конфликт authlib + launchwrapper).

exe в OneDrive — не работает автообновление (синхронизация ломает файлы). перекладывайте в D:\.

🤝 Вклад
Пулл-реквесты приветствуются. По крупным изменениям — сначала открой issue, обсудим.

## 📸 Скриншоты

| Dark |
|:-:|
| ![](screenshots/main.png)

📜 Лицензия
MIT — делай что хочешь. см. LICENSE

<div align="center">
Made by FIMOZIK

</div>
