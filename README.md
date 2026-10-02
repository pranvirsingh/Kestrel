# KESTREL — Wings over the Hollow

A vertical shooter for Android, written in Kotlin with no engine. All terrain, aircraft, effects, music and sound are generated in code.

## The campaign

There are 6 sectors, each with its own map and boss:

| Sector | Boss |
|---|---|
| Coral Coast | Leviathan |
| Dust Canyon | Sandcrawler |
| Glacier Line | Frostwall |
| Neon Sprawl | Overseer |
| Ember Forge | Crucible |
| Stratos | Hollow King |

### Threat levels
- Each sector has 4 threat levels: I Recon, II Strike, III Siege and IV Nightmare.
- Earn 3 badges at one threat level to unlock the next.

### Badges
Each sector has 4 badges per threat level:
- **Sweep:** destroy 80% of hostiles.
- **Lifeline:** rescue all 5 survivors.
- **Untouched:** take no damage.
- **Ace:** complete the sector's own challenge.

That makes 96 badges in total.

### Progression
- **Hangar:** spend the cores you collect on the main cannon, homing missiles, Arc Lance, wing drones, hull, magnet and Prism Overdrive.
- **Pilot ranks:** total badges unlock 10 permanent perks.

## What's new in 2.0
- **Real 3D art.** Every aircraft, tank, boat, turret and boss is a 3D mesh rendered in-game by a small software renderer (`R3.kt`, models in `Models.kt`).
  - Lighting: metallic Blinn-Phong, rim light, grime, panel seams, ambient occlusion and 2x supersampling.
  - The camera is tilted. The player banks as it turns, and turrets have 12 rotation frames.
  - Sprites are rendered on first launch ("ASSEMBLING SQUADRON") and cached, so later launches are quick.
- **Raised terrain.** Hills, mesas, ice shelves and slag ridges have real height (`Terrain.kt`). They hide what lies behind them, cast soft shadows and have shaded cliff faces. Ground units sit on top of them.
- **Fairer difficulty.** Threat I is much gentler:
  - fewer and slower enemy rounds, with a cap on bullets on screen;
  - enemies stop firing once they are near the bottom of the screen or close to you;
  - longer invulnerability after a hit and a stronger base cannon.
  - Nightmare stays brutal.

## Controls
- Drag anywhere to fly. The cannons fire on their own.
- Press the prism button or double-tap to trigger Overdrive. Time slows, a lance fires, and enemy rounds turn into cores.

## Build
```
./build.sh
```

## Tests
```
./t.sh BiomeShots Sheet3D UiShots Balance Pressure Campaign Monkey AudioTest
```
