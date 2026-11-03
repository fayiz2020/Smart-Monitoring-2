# Smart Monitoring

Android SMS controller for relay outputs and six configurable analog monitoring channels.

## UI refresh
- Redesigned the app with a modern control-centre header, clean navigation and a light dashboard theme.
- Relay controls are presented as appliance cards with clear ON/OFF actions and editable relay details.
- Monitoring channels use status badges and show configured thresholds; tap a channel to edit it.
- Settings are displayed as readable information cards with a dedicated edit action.
- Message activity is shown in a compact live log at the bottom.

## Build APK
This repository is configured for GitHub Actions. Push it to GitHub and run **Actions → Build APK → Run workflow** (or push a commit). Download the `SmartMonitoring-APK` artifact from the completed workflow. The APK is inside the downloaded artifact ZIP.

You can also build locally with JDK 17 and Gradle 8.7:

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Arduino
The Arduino firmware is in `arduino/SmartMonitoring/SmartMonitoring.ino`.
