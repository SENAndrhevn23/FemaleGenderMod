# TODO: Add Breast Scale Option (1x - 10x)

- [x] 1. Add `BREASTS_SCALE` config key in `Configuration.java` + register in `KEYS`
- [x] 2. Add `breastScale` field, CODEC entry, getter/setter, and `copyFrom()` update in `Breasts.java`
- [x] 3. Add optional `BreastScale` to `BreastDataComponent.java` (armor stand copying)
- [x] 4. Apply scale from armor stand data + debug info in `EntityConfig.java`
- [x] 5. Capture `breastScale` in `GenderRenderState.BreastState`
- [x] 6. Apply geometry `scale()` in `GenderLayer.setupTransformations()`
- [x] 7. Add "Breast Scale" slider in `WildfireBreastCustomizationScreen.java`
- [x] 8. Add `breast_scale` translation in `en_us.json`
- [x] 9. Bump `SyncHelloPacket.VERSION` (packet structure changed)

## Summary

Added a new "Breast Scale" slider (1.0x - 10.0x, default 1.0x) as a separate multiplier
on top of the existing "Breast Size" slider. The scale is stored on the `Breasts` object
alongside the other breast appearance settings, which means it is:

- Saved/loaded per-player through `Configuration.KEYS` (key `breasts_scale`)
- Synced over the network via the existing `Breasts.CODEC` (no new packet fields needed)
- Persisted on armor stands via `BreastDataComponent`
- Applied as a geometry scale in `GenderLayer.setupTransformations()`,
  which is inherited by `GenderArmorLayer` (so both skin + armor layers scale together)

