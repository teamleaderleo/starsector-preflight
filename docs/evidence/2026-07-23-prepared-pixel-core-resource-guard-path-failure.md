# Prepared-pixel comparison core-resource guard path failure

Date: 2026-07-23

## Result

The repaired main-menu comparison runner stopped before launching Starsector because its new core-resource guard used the wrong macOS bundle path:

```text
/Applications/Starsector.app/Contents/Resources/starfarer.res/res/data/missions/mission_list.csv
```

The exact accepted installation stores the reviewed core mission resources under:

```text
/Applications/Starsector.app/Contents/Resources/Java/data/missions
```

The operator confirmed that Finder hierarchy and supplied the installed `mission_list.csv`. The file contains the required mission entry:

```text
afistfulofcredits
```

No launcher or game process was started, no profile half was collected, and this stop provides no mod, texture-path, resource-resolution, or timing evidence.

## Repair

Replace the hardcoded root with a tested discovery helper that:

- accepts `Contents/Resources/Java` for the exact reviewed macOS installation;
- retains support for `Contents/Resources/starfarer.res/res` as an alternate packaged layout;
- requires both `data/missions/mission_list.csv` and `data/missions/afistfulofcredits/descriptor.json`;
- requires the mission list to contain `afistfulofcredits`;
- rejects missing or ambiguous roots;
- returns the exact resolved paths for hashing and retained identity evidence.

The comparison remains unrun. Do not attribute this guard failure to mods or use it as performance evidence.
