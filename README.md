<div align="center">

<img src="src/main/resources/worm_logo.png" alt="worm launcher" width="180"/>

# worm launcher

**A modern, customizable Minecraft launcher with multi-loader support.**

[![Version](https://img.shields.io/badge/version-1.0.3-00008B?style=flat-square)](https://github.com/FIMOZIK/worm-client/releases)
[![License](https://img.shields.io/badge/license-MIT-FFA07A?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/java-21+-00008B?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/platform-Windows-FFA07A?style=flat-square&logo=windows)](https://github.com/FIMOZIK/worm-client/releases)

</div>

---

## ✨ Возможности

- 🎮 **Мультизагрузчик** — Vanilla, Fabric, Forge, Quilt из коробки
- 🔐 **Три способа входа** — Ely.by (пароль), Ely.by (OAuth в браузере), Microsoft (device code)
- 🎨 **7 тем оформления** — WormLauncher, Cyberpunk, Blood, Aqua, Mono, Sunset, Mint + light/dark
- 🖼 **Кастомизация фона** — PNG/JPG/GIF/MP4 с настройкой прозрачности
- 🔊 **Звук нажатия клавиш** — свой WAV/MP3/MP4
- 🔤 **Свои шрифты** — TTF/OTF из файла
- 📦 **Профили сборок** — сохраняй
- например - «1.21.11 + Fabric 0.20.1» под своим именем
- 🎯 **Discord Rich Presence** — статус, кнопки, голова скина
- 🔄 **Автообновление** — через GitHub Releases, скачивание и самоперезапуск
- 🎨 **Умный установщик** — выбор папок, ярлык на рабочем столе, свой значок
- 🌐 **Два языка** — Русский и English (переключается в настройках)
- 🗑 **Гибкое удаление** — с выбором «что удалять»: лаунчер, MC или оба

---

## 📥 Установка

### Вариант 1 — готовый .exe

1. Скачай последний `WormLauncher-1.0.3.zip` из [Releases](https://github.com/FIMOZIK/worm-client/releases)
2. Распакуй в любую папку
3. Запусти `WormLauncher.exe`
4. При первом запуске выбери:
   - язык (RU / EN)
   - папку для лаунчера
   - папку для Minecraft
   - создать ли ярлык на рабочем столе

### Вариант 2 — из jar

java -jar worm-launcher-1.0.4.jar
Требуется Java 21+.

🗑 Удаление
Через лаунчер — ⚙ Настройки → «Удалить лаунчер...»

Через отдельный exe — WormUninstaller.exe

Через аргумент — WormLauncher.exe --uninstall

При удалении можно выбрать: удалять ли файлы Minecraft (сохранится game/ с мирами и модами) или только файлы лаунчера.

🎨 Темы
WormLauncher	Cyberpunk	Blood
Aqua	Mono	Sunset
Все темы работают в двух режимах — dark и light. Активная тема и режим переключаются в ⚙ Настройках.

🔧 Сборка из исходников
bash
git clone https://github.com/FIMOZIK/worm-client.git
cd worm-client

# сборка jar
gradlew.bat build

# результат
# build/libs/worm-launcher-1.0.4.jar
Требования: JDK 21, Windows 10/11.

Сборка .exe (опционально)
bash
cd build/libs
jpackage --type app-image --name WormLauncher --app-version 1.0.4 ^
  --input . --main-jar worm-launcher-1.0.4.jar ^
  --main-class launcher.LauncherMain ^
  --java-options "-Dfile.encoding=UTF-8"
🤝 Вклад
Пулл-реквесты приветствуются. По крупным изменениям — сначала открой issue, обсудим.

📜 Лицензия
MIT — делай что хочешь.

<div align="center">
Made by FIMOZIK

🪱 W — DarkBlue, LightSalmon

</div>
