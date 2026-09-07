# Hikari V2 Cutover Provenance

- Strategy: same Hikari Git repository; isolated V2 branch/workspace
- V1 cutover base commit: `f4a2796e557e1bca6d22c0582b5e7197266411e5`
- V2 cutover branch: `v2/foundation-clean-boot`
- Origin at cutover: `https://github.com/Toanhp123/Hikari.git`
- Second repository/project root: forbidden
- Peer `:app-v2`: forbidden
- Android project strategy: rebuild the product shell in the existing repository; retain/quarantine approved modules in place rather than copying them into another project
