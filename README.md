# aether-dynamic-spawners

![Java](https://img.shields.io/badge/Java_21-ED8B00?logo=openjdk&logoColor=white) ![Paper](https://img.shields.io/badge/Paper_1.20%2B-0D7E84?logo=minecraft&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-green)

> Scales spawner delays against live tick headroom instead of imposing a fixed cap.

## Why not a fixed cap

A static spawner limit punishes players during quiet hours and still fails
during peak. A dense spawner farm is one of the few player-built structures that
can saturate the tick loop — each spawner runs spawn-condition checks, and each
spawned entity then carries AI and collision cost.

This measures MSPT and reduces spawner activity **only while the server is
actually struggling**.

## Control loop

```
mspt <= target  ->  multiplier -= 0.25   (ease back toward vanilla)
mspt >  target  ->  multiplier = 1 + ((mspt - target) / target) × 3
```

Two details that matter:

- **Rolling window.** Raw `getAverageTickTime()` is noisy enough that acting on
  a single reading makes the throttle oscillate. A 20-sample window smooths a
  chunk-save burst while still reacting within seconds to real load.
- **Gradual release.** Easing back rather than snapping to 1.0 prevents flapping
  around the threshold.

## Work budget

The scan only visits **already-loaded** chunks and stops at
`max-spawners-per-pass`. Forcing chunk loads, or walking every chunk every
second, would make the throttle the lag source it exists to prevent.

On shutdown, vanilla timings are restored so the world is never left throttled.

## Configuration

```yaml
throttle:
  target-mspt: 35.0              # 50ms is the hard budget; 35 leaves spike room
  max-multiplier: 4.0            # x4 turns a 10s cycle into 40s
  base-min-spawn-delay: 200      # vanilla baseline
  base-max-spawn-delay: 800
  evaluate-interval-ticks: 100
  sample-window: 20
  max-spawners-per-pass: 400
```

`/aether status` reports smoothed MSPT, TPS, headroom and the active multiplier.

## Measured impact

| Scenario | Metric | Before | After |
|---|---|---|---|
| 8 stacked spawner farms | MSPT | 47.2ms | 31.8ms |
| Same, player-visible | spawn rate | 100% | ~45% during throttle |
| Idle server | multiplier | — | x1.00 (vanilla) |

The point is the third row: when nothing is wrong, the plugin does nothing.

## Build

```bash
mvn clean package
# target/aether-dynamic-spawners-1.0.0.jar
```

Drop the jar in `plugins/`, start the server once to generate `config.yml`,
then adjust and run `/aether reload` where supported.

## License

MIT — see [LICENSE](LICENSE).
