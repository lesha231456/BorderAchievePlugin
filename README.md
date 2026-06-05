# BorderAchieve / BorderAchieve

| [![дс](https://cdn.modrinth.com/data/cached_images/8bcf7ad411d848088f96b0f7f559e3906c2a4dfc.png)](https://discord.com/invite/YeYxsWFybN) | [![мд](https://cdn.modrinth.com/data/cached_images/942bcc29cce40415c2fe7faecb6a029d78c40f9e.png)](https://modrinth.com/user/lesha_1) | [![гд](https://cdn.modrinth.com/data/cached_images/f5b2e575a73b1a729a0be9541cf3497764356738.png)](https://github.com/lesha231456) | [![сн](https://cdn.modrinth.com/data/cached_images/2aedb989a7a0e75e223509381cf9778997b3ea89.png)](https://ru.namemc.com/profile/lesha_1.1) |
|---------------------|:---------------------:|:---------------------:|---------------------:|

Плагин для Paper 1.21+, связывающий достижения (advancements) с динамическим расширением границ миров и блокировкой измерений.  
A Paper 1.21+ plugin that links advancements to dynamic world border expansion and dimension locking.

---
РУССКАЯ ВЕРСИЯ 
---

## 📖 Описание

**BorderAchieve** превращает получение достижений в механику, влияющую на мир сервера:

- **Расширение границ** — за каждое новое достижение граница в выбранных мирах увеличивается на заданное количество блоков. В Незере расширение автоматически делится на 8.
- **Блокировка миров** — телепортация в определённые миры (например, Ад, Энд) запрещена, пока общее количество выполненных достижений на сервере не достигнет порога. После разблокировки мир открывается навсегда с общим оповещением.
- **Топ игроков** — интерактивное GUI-меню со списком лучших игроков по числу достижений.
- **Визуальные и звуковые эффекты** — настраиваемые Title, Subtitle и звук при расширении границы.
- **PlaceholderAPI** — поддержка плейсхолдеров для интеграции с другими плагинами (табло, голограммы и т.п.).
- **Гибкая настройка** — режимы расширения (плавный/отложенный), время, выбор миров, индивидуальные пороги блокировки для каждого измерения.

## 📦 Установка

1. Скачайте файл плагина.
2. Поместите `BorderAchieve.jar` в папку `plugins/`.
3. Перезапустите сервер или выполните `/reload`.
4. Настройте конфигурационный файл `plugins/BorderAchieve/config.yml`.
5. Настройте языковые файлы в `plugins/BorderAchieve/language/` (ru.yml / en.yml).
6. (Опционально) Установите PlaceholderAPI для использования плейсхолдеров.

## 🛠 Команды

| Команда | Право | Описание |
|----------|--------|------------|
| `/topborder` (или `/bordertop`) | – | Открыть GUI топ достижений |
| `/borderreload` | `borderachieve.admin` | Перезагрузить конфиг и языковые файлы |
| `/borderunlock <мир> [игрок]` | `borderachieve.admin` | Разблокировать мир навсегда или выдать временный пропуск игроку |
| `/borderlockstatus` | `borderachieve.admin` | Посмотреть статус блокировки миров |
| `/borderdebug` | `borderachieve.admin` | Показать отладочную информацию |
| `/borderachieve` | – | Справка по плагину |

## 📊 PlaceholderAPI

Если PlaceholderAPI установлен, доступны следующие плейсхолдеры:

- `%borderachieve_count%` — количество достижений игрока
- `%borderachieve_rank%` — место в топе
- `%borderachieve_total%` — общее количество выполненных достижений на сервере
- `%borderachieve_locked_<мир>%` — `true`, если мир заблокирован для игрока; `false`, если открыт
# Info:
<details>
<summary>Info:3</summary>

## ⚙ Конфигурация

Основные настройки в `config.yml`:

```yaml
language: ru
settings:
  enable_border_expansion: true
  expand_amount: 15.0
  border_change_mode: "smooth"
  border_move_speed: "200t"
  set_initial_border_on_first_start: true
  initial_border_size: 35.0
  apply_to_all_worlds: false
  allowed_worlds:
    - world
    - world_nether
    - world_the_end
  notify_only_player: true
  effects:
    sound: "entity.ender_dragon.growl"
    sound_volume: 0.8
    sound_pitch: 1.0
    title: true
    subtitle: true
gui:
  slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43]
world_locking:
  enabled: true
  default_required: 150
  worlds:
    world_nether:
      required_achievements: 150
    world_the_end:
      required_achievements: 400
  show_deny_title: true
debug: false
```

Подробное описание всех параметров находится в `documentation.yml` (создаётся автоматически).

## 🔒 Рекомендации по блокировке миров

Для 10–15 активных игроков рекомендуемые пороги:

- **Ад**: `150–200` (откроется не сразу, после накопления стартовых достижений)
- **Энд**: `400–500` (серьёзное испытание, откроется позже)

Проверяйте прогресс командой `/borderlockstatus`.

## 🌐 Языки

Поддерживаются русский (`ru`) и английский (`en`). Выберите нужный в `config.yml` параметром `language`. Языковые файлы находятся в папке `plugins/BorderAchieve/language/`.

## 🧪 Отладка

Включите `debug: true` в конфигурации, чтобы получать подробные логи в консоль.

## 🔄 Сброс настроек

Удалите файл `config.yml` и перезапустите сервер — плагин создаст новый с настройками по умолчанию.

</details>

---
ENGLISH VERSION
---

## 📖 Description

**BorderAchieve** turns earning advancements into a world‑affecting mechanic:

- **Border expansion** – every new achievement expands the border in selected worlds by a configurable amount. In the Nether the expansion is automatically divided by 8.
- **World locking** – teleportation to specific worlds (e.g. Nether, End) is blocked until the server‑wide total of completed achievements reaches a threshold. Once unlocked, the world stays open forever with a broadcast.
- **Leaderboard** – an interactive GUI ranking players by their completed achievements.
- **Visual & sound effects** – customizable Title, Subtitle, and sound when the border expands.
- **PlaceholderAPI** – placeholders for integration with other plugins (scoreboards, holograms, etc.).
- **Flexible configuration** – expansion mode (smooth/delayed), time, world selection, per‑world locking thresholds.

## 📦 Installation

1. Download the plugin file.
2. Place `BorderAchieve.jar` into the `plugins/` folder.
3. Restart the server or run `/reload`.
4. Configure `plugins/BorderAchieve/config.yml`.
5. Adjust language files in `plugins/BorderAchieve/language/` (ru.yml / en.yml).
6. (Optional) Install PlaceholderAPI to use placeholders.

## 🛠 Commands

| Command | Permission | Description |
|----------|------------|--------------|
| `/topborder` (or `/bordertop`) | – | Open achievement leaderboard GUI |
| `/borderreload` | `borderachieve.admin` | Reload config & language files |
| `/borderunlock <world> [player]` | `borderachieve.admin` | Unlock world permanently or grant temp access to a player |
| `/borderlockstatus` | `borderachieve.admin` | View world locking status |
| `/borderdebug` | `borderachieve.admin` | Show debug info |
| `/borderachieve` | – | Plugin help |

## 📊 PlaceholderAPI

If PlaceholderAPI is installed, the following placeholders are available:

- `%borderachieve_count%` – player’s achievement count
- `%borderachieve_rank%` – player’s rank
- `%borderachieve_total%` – total completed advancements on the server
- `%borderachieve_locked_<world>%` – `true` if world is locked for the player, `false` if open

# Info:
<details>
<summary>Info:3</summary>

## ⚙ Configuration

Main settings in `config.yml`:

```yaml
language: en
settings:
  enable_border_expansion: true
  expand_amount: 15.0
  border_change_mode: "smooth"
  border_move_speed: "200t"
  set_initial_border_on_first_start: true
  initial_border_size: 35.0
  apply_to_all_worlds: false
  allowed_worlds:
    - world
    - world_nether
    - world_the_end
  notify_only_player: true
  effects:
    sound: "entity.ender_dragon.growl"
    sound_volume: 0.8
    sound_pitch: 1.0
    title: true
    subtitle: true
gui:
  slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43]
world_locking:
  enabled: true
  default_required: 150
  worlds:
    world_nether:
      required_achievements: 150
    world_the_end:
      required_achievements: 400
  show_deny_title: true
debug: false
```

For a full description of every parameter, see the automatically created `documentation.yml`.

## 🔒 Locking Recommendations

For 10–15 active players, recommended thresholds:

- **Nether**: `150–200` (won't unlock instantly, after initial advancements accumulate)
- **End**: `400–500` (a real challenge, unlocks much later)

Check progress with `/borderlockstatus`.

## 🌐 Languages

Russian (`ru`) and English (`en`) are supported. Set the `language` option in `config.yml`. Language files are located in `plugins/BorderAchieve/language/`.

## 🧪 Debugging

Enable `debug: true` in the config to receive detailed console logs.

## 🔄 Resetting to Defaults

Delete `config.yml` and restart the server — the plugin will generate a fresh default configuration.

</details>

## 📝 Автор | Author

lesha_1

## 📃 Лицензия | License

MIT License. Подробнее см. в файле [LICENSE](https://github.com/lesha231456/BorderAchievePlugin/blob/main/LICENSE).  
MIT License. See the [LICENSE](https://github.com/lesha231456/BorderAchievePlugin/blob/main/LICENSE) file for details.
