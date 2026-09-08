# LXAC (Backend – Paper/Spigot)

PacketEvents-based anticheat foundation by Luminex Studios with Velocity support.

## Features

- **CheckManager** – easy registration of new checks  
  ```java
  checkManager.register("FlightA", 30, new FlightAChecker(this));
  checkManager.register("SpeedA", 25, new SpeedAChecker(this));
  ```
- **ViolationManager** with timed decay + full reset
- **PlayerData** system – ready for buffers, timestamps, custom data per check
- **FlagManager** – alerts admins + sends to Velocity proxy
- **Velocity bridge** – network-wide kick with server name in logs
- Single-server fallback (local kick)
- Fully configurable thresholds & enable/disable per check
- Everything in English

## Requirements
- Paper / Spigot 1.20.4+ (Java 17)
- PacketEvents (Spigot) installed as a plugin

## How to add a new check later

1. Create a class extending `Check`:
   ```java
   public class SpeedAChecker extends Check {
       public SpeedAChecker(LXAC plugin) {
           super(plugin, "SpeedA");
       }

       @Override
       public void onPacketReceive(PacketReceiveEvent event) {
           // your detection logic
           flag(player, "extra info");
       }
   }
   ```

2. Register it in `LXAC.onEnable()`:
   ```java
   checkManager.register("SpeedA", 25, new SpeedAChecker(this));
   ```

3. Add to `config.yml`:
   ```yaml
   checks:
     SpeedA:
       enabled: true
       threshold: 25
   ```

## Commands
- `/lxac reload`
- `/lxac info`
- `/lxac violations <player>`

Aliases: `/anticheat`, `/ac`

## Permissions
- `lxac.admin` – alerts + commands
- `lxac.bypass` – bypass all checks

## Package
`com.luminex_studios.lxac`
