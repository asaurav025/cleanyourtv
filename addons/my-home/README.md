# My Home

A personal companion for the Clean Your TV launcher that ties Home to the room and the trading day. The launcher
itself stays offline: it only tells this app the focused tile's colour, an app opened from Home and Home coming
back (`call()`s to `HomeProvider`), and shows the one line this app answers.

| Part | What it does |
|---|---|
| Ambient sync | ESPHome RGB strips follow the focused app's colour (saturated for LEDs; greys become warm white). Coalesced and rate-limited, since each colour is a BLE write behind an ESP32. |
| Film mode | Opening an app from `movie.apps` dims the fairy lights and turns the strips into a soft bias light; Home brings them back. |
| Night | From `night.from` to `night.to`: warm, dim strips and fairy lights, and the greeting becomes `night.greeting`. |
| Market strip | 09:00-15:30 IST on NSE trading days (2026 holidays built in): delayed Yahoo quotes under the clock, ▲ green, ▼ red, "Flat by 15:10" before the close. Hidden when the market is shut. |
| Opening bell | The fairy lights flash once at 09:15 and at flat-by (ESPHome returns them to how they were). |
| Diwali | Between `diwali.from` and `diwali.to`, after `diwali.evening`, the fairy lights run `diwali.effect`. Pair it with the launcher's `festival: diwali` skin. |
| Note | A scribble from a phone on Home, below the top-right buttons. Open `http://<tv ip>:8765/` on the same Wi-Fi, draw and send; it stays until it is replaced or removed, and fades out over the last day of `note.fade_days` (15; 0 never fades). Anyone on the network can send one: there is no login. Needs a launcher built from the `my-home` branch. |
| Weather and sun | With `weather.latitude` and `.longitude` set, Open-Meteo's current weather and today's sunrise and sunset every 20 minutes, handed to Home as the scene ("dusk/rain") for the launcher's `live_scene`. Round to two decimals: a forecast cell is a kilometre or more, and the place is sent with each request. |

## Permissions

`INTERNET`, for the ESPHome boards on your home network (plain HTTP), Yahoo's delayed quotes, Open-Meteo's weather (HTTPS) and the note page it serves to the home network on port 8765 (plain HTTP, no login). No
analytics, no account, nothing else. Its `HomeProvider` answers only the launcher (`com.gathin.tvhome`) and the ADB
shell.

`install.sh` also allows the app to start in the background and exempts it from battery optimisation, so the note page keeps answering. A foreground service keeps it awake; Android would otherwise freeze it.

Rules: it never switches a light on by itself (apart from the bell's flash and the card), and sync, film and night
only touch lights that are already on.

```bash
TV_HOST=<tv ip> addons/my-home/install.sh my-theme/myhome.json   # start from myhome.example.json
TV_HOST=<tv ip> tools/theme.sh my-theme                           # a theme with "paired_app": "dev.asaurav.myhome"
```

Use IP addresses for `host` (give the boards DHCP reservations): Android TV does not reliably resolve `.local`.
Test without the launcher: `tools/tv.py 'content call --uri content://dev.asaurav.myhome.home --method focus --arg "#E50914"'`.

Needs a launcher built from the `my-home` branch, which adds the `paired_app` theme key (`Companion.java`).
