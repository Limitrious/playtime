# PlayTime (fork by limitrious)

Simple Minecraft plugin to track player playtime and show stats.
Original: https://github.com/Twi5TeD/PlayTime

**Requirements**
- Java 17
- Maven (for building)
- Paper/Spigot server; PlaceholderAPI recommended for placeholders

**Build**
Run from the project root:

```bash
mvn -f pom.xml clean package
```

Generated JAR: `target/playtime-1.0.0-SNAPSHOT.jar`

**Install**
- Place the built JAR into your server's `plugins/` folder and restart the server.

**Commands**
- `/playtime` — show your playtime
- `/playtime <player>` — show another player's playtime
- `/playtime top` — show top players
- `/playtime reload` — reload config
- `/playtime uptime` — show server uptime

**Permissions**
- `playtime.check`
- `playtime.checkothers`
- `playtime.checktop`
- `playtime.reload`
- `playtime.uptime`
