# Integrating with Bifrost

Bifrost exposes a broadcast-based IPC API that lets other apps on the device drive its LEDs — flashing police lights during a car chase, matching the LED colour to a character's health bar, setting a preset for your launcher wallpaper, reacting to in-game events in real time. It is entirely opt-in: the user must enable **"Allow third-party LED control"** in Bifrost settings before any command is accepted.

> **Minimum Bifrost version:** 1.2.0-beta  
> **API version:** 1  
> **Min Android SDK for callers:** 33 (same as Bifrost itself)

---

## Contents

1. [Quick orientation](#1--quick-orientation)
2. [Declare the permission](#2--declare-the-permission)
3. [Copy the constants](#3--copy-the-constants)
4. [Send a command](#4--send-a-command)
5. [Check if Bifrost is available](#5--check-if-bifrost-is-available)
6. [Available effects](#6--available-effects)
7. [ACTION_DISPLAY — live override](#7--action_display--live-override)
8. [ACTION_CLEAR](#8--action_clear)
9. [ACTION_INSTALL_PROFILE / ACTION_UNINSTALL_PROFILE](#9--action_install_profile--action_uninstall_profile)
10. [Result codes](#10--result-codes)
11. [Complete wrapper class](#11--complete-wrapper-class)
12. [Java example](#12--java-example)
13. [Recipes](#13--recipes)
14. [Common mistakes](#14--common-mistakes)
15. [API versioning](#15--api-versioning)

---

## 1 — Quick orientation

| Concept | What it means for you |
|---|---|
| **Commands are broadcasts** | Send an explicit ordered broadcast; read the result code if you need acknowledgement. |
| **Your override is a removable layer** | Bifrost snapshots its current state when your override starts and restores it automatically when it ends. App-profile switching is paused while an override is active. |
| **Bifrost must already be running** | You cannot start the foreground service remotely (Android 12+ restriction). If it's not running, commands are silently dropped. Design gracefully for this. |
| **Rate limit: 8 burst / 4 per second** | Per calling UID. Burst budget refills at 4 tokens/s. |
| **Priority arbitrates between apps** | If two apps both try to drive the LEDs, the higher-priority command wins. Each app can always update its own active override regardless of priority. |
| **No library dependency needed** | Copy the string constants below. The API surface is intentionally minimal. |

---

## 2 — Declare the permission

Add one line to your `AndroidManifest.xml`. This is a `normal`-level permission — Android grants it automatically at install time; no runtime prompt is needed.

```xml
<uses-permission android:name="com.moonbench.bifrost.permission.CONTROL_LEDS" />
```

The user still has to flip **"Allow third-party LED control"** inside Bifrost settings. Your commands are silently rejected until they do.

---

## 3 — Copy the constants

There is no library or AAR to depend on. Copy this object into your project:

```kotlin
object BifrostApi {

    // ── Identity ──────────────────────────────────────────────────────────
    const val PERMISSION         = "com.moonbench.bifrost.permission.CONTROL_LEDS"
    const val RECEIVER_PACKAGE   = "com.moonbench.bifrost"
    const val RECEIVER_CLASS     = "com.moonbench.bifrost.external.ExternalApiReceiver"

    // ── Actions ───────────────────────────────────────────────────────────
    const val ACTION_DISPLAY           = "com.moonbench.bifrost.api.ACTION_DISPLAY"
    const val ACTION_CLEAR             = "com.moonbench.bifrost.api.ACTION_CLEAR"
    const val ACTION_INSTALL_PROFILE   = "com.moonbench.bifrost.api.ACTION_INSTALL_PROFILE"
    const val ACTION_UNINSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_UNINSTALL_PROFILE"

    // ── Protocol ──────────────────────────────────────────────────────────
    const val API_VERSION        = 1
    const val EXTRA_API_VERSION  = "apiVersion"  // Int — always include
    const val EXTRA_REQUEST_ID   = "requestId"   // String? ≤ 64 chars, echoed in result data

    // ── DISPLAY / INSTALL_PROFILE shared extras ───────────────────────────
    const val EXTRA_EFFECT          = "effect"          // String — see §6
    const val EXTRA_COLOR           = "color"           // Int (ARGB packed) — left LED
    const val EXTRA_COLOR_RIGHT     = "colorRight"      // Int (ARGB packed) — right LED; default = color
    const val EXTRA_INTENSITY       = "intensity"       // Int 0–255 (or 0–intensityScale)
    const val EXTRA_INTENSITY_SCALE = "intensityScale"  // Int 1–255; normalises intensity range
    const val EXTRA_SPEED           = "speed"           // Float 0.0–1.0
    const val EXTRA_SMOOTHNESS      = "smoothness"      // Float 0.0–1.0
    const val EXTRA_SENSITIVITY     = "sensitivity"     // Float 0.0–1.0

    // ── DISPLAY-only extras ───────────────────────────────────────────────
    const val EXTRA_PRIORITY     = "priority"    // Int 0–100; default 50
    const val EXTRA_DURATION_MS  = "durationMs"  // Long 1–600_000
    const val EXTRA_UNTIL        = "until"       // String "NEXT_COMMAND" | "EXPLICIT_CLEAR"
    const val EXTRA_INDEFINITE   = "indefinite"  // Boolean — alias for UNTIL_EXPLICIT_CLEAR

    const val UNTIL_NEXT_COMMAND   = "NEXT_COMMAND"
    const val UNTIL_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"

    // ── INSTALL_PROFILE additional extras ────────────────────────────────
    const val EXTRA_PROFILE_NAME              = "profileName"
    const val EXTRA_PROFILE_REPLACE_IF_EXISTS = "replaceIfExists"      // Boolean
    const val EXTRA_SATURATION_BOOST          = "saturationBoost"      // Float 0.0–1.0
    const val EXTRA_USE_CUSTOM_SAMPLING       = "useCustomSampling"    // Boolean
    const val EXTRA_USE_SINGLE_COLOR          = "useSingleColor"       // Boolean
    const val EXTRA_BREATHE_WHEN_CHARGING     = "breatheWhenCharging"  // Boolean
    const val EXTRA_INDICATE_CHARGING_SPEED   = "indicateChargingSpeed" // Boolean
    const val EXTRA_FLASH_WHEN_READY          = "flashWhenReady"       // Boolean
    const val EXTRA_BATTERY_LOW_COLOR         = "batteryLowColor"      // Int (ARGB) optional
    const val EXTRA_BATTERY_MID_COLOR         = "batteryMidColor"      // Int (ARGB) optional
    const val EXTRA_BATTERY_HIGH_COLOR        = "batteryHighColor"     // Int (ARGB) optional
    const val EXTRA_CPU_COOL_COLOR            = "cpuCoolColor"         // Int (ARGB) optional
    const val EXTRA_CPU_WARM_COLOR            = "cpuWarmColor"         // Int (ARGB) optional
    const val EXTRA_CPU_HOT_COLOR             = "cpuHotColor"          // Int (ARGB) optional

    // ── Result codes (ordered broadcasts only) ───────────────────────────
    const val RESULT_ACCEPTED              =  0
    const val RESULT_REJECTED_DISABLED     = -1  // toggle is off in Bifrost settings
    const val RESULT_REJECTED_VERSION      = -2  // apiVersion mismatch
    const val RESULT_REJECTED_VALIDATION   = -3  // bad/missing extras
    const val RESULT_REJECTED_UNAUTHORIZED = -4  // caller UID could not be resolved
    const val RESULT_REJECTED_RATE_LIMITED = -5  // > 4 commands/s
    const val RESULT_REJECTED_UNKNOWN_ACTION = -6
}
```

---

## 4 — Send a command

All commands go to the same receiver via **explicit broadcast** (package + class required on Android 8+).

```kotlin
// Helper — fire and forget
fun sendToBifrost(context: Context, action: String, fill: Intent.() -> Unit = {}) {
    val intent = Intent(action).apply {
        setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
        putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
        fill()
    }
    context.sendBroadcast(intent, BifrostApi.PERMISSION)
}

// Helper — with result callback
fun sendToBifrostForResult(
    context: Context,
    action: String,
    fill: Intent.() -> Unit = {},
    onResult: (code: Int, requestId: String?) -> Unit,
) {
    val intent = Intent(action).apply {
        setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
        putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
        fill()
    }
    context.sendOrderedBroadcast(
        intent,
        BifrostApi.PERMISSION,
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                onResult(resultCode, resultData)
            }
        },
        null, Activity.RESULT_OK, null, null
    )
}
```

Use `sendBroadcast` for fire-and-forget (the common case). Use `sendOrderedBroadcast` only when you need to know whether the command was accepted — debug tooling, first-run checks, rate-limit back-off, etc.

---

## 5 — Check if Bifrost is available

Bifrost won't be installed on every device. Guard your calls:

```kotlin
fun isBifrostInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(BifrostApi.RECEIVER_PACKAGE, 0)
        true
    }.getOrDefault(false)

// In your manifest, declare a <queries> block so PackageManager
// lets you inspect Bifrost's presence:
```

```xml
<queries>
    <package android:name="com.moonbench.bifrost" />
</queries>
```

You do **not** need to add the `<queries>` block just to send broadcasts — Android routes explicit broadcasts regardless. The block is only needed if you call `getPackageInfo`, `queryIntentActivities`, or similar package-inspection APIs.

---

## 6 — Available effects

Pass one of these strings as `EXTRA_EFFECT`. Effects requiring screen capture (`AMBILIGHT`, `AUDIO_REACTIVE`, `AMBIAURORA`) are blocked in the external API — they need a MediaProjection consent flow that only Bifrost's own UI can trigger.

| Effect name | Color | R/L independent | Speed | Smoothness | Sensitivity | Notes |
|---|---|---|---|---|---|---|
| `STATIC` | ✓ | ✓ | — | — | — | Solid colour, no animation |
| `BREATH` | ✓ | ✓ | ✓ | — | — | Slow fade in/out |
| `PULSE` | ✓ | ✓ | ✓ | — | — | Sharp pulse |
| `STROBE` | ✓ | ✓ | ✓ | — | — | Hard on/off flash |
| `SPARKLE` | ✓ | ✓ | ✓ | — | — | Random pixel flicker |
| `FADE_TRANSITION` | ✓ | ✓ | ✓ | — | — | Smooth colour cycling |
| `CHASE` | ✓ | ✓ | ✓ | — | — | Sweeping left↔right |
| `RAINBOW` | — | — | ✓ | — | — | Full-spectrum cycle |
| `RAVE` | — | — | ✓ | — | — | Fast random colour changes |
| `BATTERY_INDICATOR` | — | — | — | — | — | Shows battery level |
| `CPU_TEMPERATURE` | — | — | — | — | — | Shows CPU temp |

**Color** — whether `EXTRA_COLOR` / `EXTRA_COLOR_RIGHT` have any effect.  
**R/L independent** — whether left and right LEDs can be different colours.

---

## 7 — `ACTION_DISPLAY` — live override

Drives the LEDs immediately. Bifrost snapshots its current state, applies your command, and reverts when the override ends.

```kotlin
// Police lights for 4 seconds
sendToBifrost(context, BifrostApi.ACTION_DISPLAY) {
    putExtra(BifrostApi.EXTRA_EFFECT,       "STROBE")
    putExtra(BifrostApi.EXTRA_COLOR,        Color.RED)
    putExtra(BifrostApi.EXTRA_COLOR_RIGHT,  Color.BLUE)
    putExtra(BifrostApi.EXTRA_SPEED,        0.85f)
    putExtra(BifrostApi.EXTRA_INTENSITY,    255)
    putExtra(BifrostApi.EXTRA_DURATION_MS,  4_000L)
    putExtra(BifrostApi.EXTRA_PRIORITY,     70)
    putExtra(BifrostApi.EXTRA_REQUEST_ID,   "chase-001")
}
```

### Terminator — how long does the override last?

Specify exactly **one**. Combining them is a validation error.

| Terminator | What happens |
|---|---|
| `EXTRA_DURATION_MS` (Long, 1–600 000) | Override ends automatically after this many milliseconds. |
| `EXTRA_UNTIL = UNTIL_NEXT_COMMAND` | Ends when your app sends the next DISPLAY or CLEAR. **Default when nothing is set.** |
| `EXTRA_UNTIL = UNTIL_EXPLICIT_CLEAR` | Persists until your app sends `ACTION_CLEAR`. |
| `EXTRA_INDEFINITE = true` | Identical to `UNTIL_EXPLICIT_CLEAR`. Provided for readability. |

When the override ends (for any reason), Bifrost reverts to whatever it was doing before: if app-profile switching was active it re-resolves the current foreground app immediately; otherwise it restores the user's last preset.

### Priority

`EXTRA_PRIORITY` is an integer 0 (lowest) to 100 (highest), default 50.

- Your own app can **always** replace its current override with a new command, regardless of priority.
- A command from a **different** app is silently dropped if the active override has a strictly higher priority.
- When priorities are equal, last-write-wins.

Use high priority (≥80) for urgent, user-visible notifications. Use default (50) for ambient effects. Reserve low priority (<30) for purely cosmetic "nice-to-have" colouring that shouldn't interfere with anything else.

### Intensity scale

If your game or app has its own brightness range, tell Bifrost the scale so it can normalise correctly:

```kotlin
// Your brightness is 0–100, not 0–255
putExtra(BifrostApi.EXTRA_INTENSITY,       75)   // value
putExtra(BifrostApi.EXTRA_INTENSITY_SCALE, 100)  // max of your range
```

---

## 8 — `ACTION_CLEAR`

Immediately ends your app's active override. You can only clear your own override.

```kotlin
sendToBifrost(context, BifrostApi.ACTION_CLEAR)
```

Always call this from `onPause` / `onStop` if you used `UNTIL_EXPLICIT_CLEAR` or `UNTIL_NEXT_COMMAND`, so Bifrost doesn't stay stuck on your effect after the user leaves your app.

---

## 9 — `ACTION_INSTALL_PROFILE` / `ACTION_UNINSTALL_PROFILE`

Install a named preset that appears in Bifrost's preset carousel alongside the user's own presets. Good for shipping a branded theme with your app.

```kotlin
// Install on first launch
sendToBifrost(context, BifrostApi.ACTION_INSTALL_PROFILE) {
    putExtra(BifrostApi.EXTRA_PROFILE_NAME,              "Emerald Trail")
    putExtra(BifrostApi.EXTRA_EFFECT,                    "CHASE")
    putExtra(BifrostApi.EXTRA_COLOR,                     Color.GREEN)
    putExtra(BifrostApi.EXTRA_COLOR_RIGHT,               Color.rgb(0, 200, 80))
    putExtra(BifrostApi.EXTRA_INTENSITY,                 200)
    putExtra(BifrostApi.EXTRA_SPEED,                     0.6f)
    putExtra(BifrostApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, true)
}

// Remove on explicit uninstall flow (optional — Bifrost auto-cleans on package removal)
sendToBifrost(context, BifrostApi.ACTION_UNINSTALL_PROFILE) {
    putExtra(BifrostApi.EXTRA_PROFILE_NAME, "Emerald Trail")
}
```

**Ownership:** presets are tagged with your package name. You can only uninstall presets your package installed. Bifrost removes all your presets automatically when Android broadcasts your app's uninstall.

**Visibility:** the preset appears the next time the user opens Bifrost (the UI reloads from SharedPreferences on resume).

**`replaceIfExists`:** if `false` (default), a second install with the same name is a no-op. Set to `true` to update the preset on each app version upgrade.

---

## 10 — Result codes

These are only returned when you use `sendOrderedBroadcast`. Plain `sendBroadcast` gives you no feedback.

| Code | Value | Meaning |
|---|---|---|
| `RESULT_ACCEPTED` | 0 | Command accepted and dispatched (or stored, for profile installs). |
| `RESULT_REJECTED_DISABLED` | -1 | The "Allow third-party LED control" toggle is off. |
| `RESULT_REJECTED_VERSION` | -2 | `apiVersion` doesn't match `API_VERSION = 1`. |
| `RESULT_REJECTED_VALIDATION` | -3 | Bad or missing extras (unknown effect name, out-of-range duration, conflicting terminators, etc.). |
| `RESULT_REJECTED_UNAUTHORIZED` | -4 | Caller UID couldn't be resolved to a package. Should not happen under normal circumstances. |
| `RESULT_REJECTED_RATE_LIMITED` | -5 | More than ~4 commands/second from your UID. Back off and retry. |
| `RESULT_REJECTED_UNKNOWN_ACTION` | -6 | Action string not recognised. Check for typos. |

`resultData` contains your `requestId` string when the result is `RESULT_ACCEPTED`, useful for correlating asynchronous acknowledgements.

---

## 11 — Complete wrapper class

Drop this into your project for a clean API surface:

```kotlin
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log

/**
 * Thin wrapper for sending commands to Bifrost.
 * No state is held; all methods are stateless helpers.
 */
object Bifrost {

    private const val TAG = "Bifrost"

    // ── Availability ──────────────────────────────────────────────────────

    /** Returns true if Bifrost is installed on this device. */
    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(BifrostApi.RECEIVER_PACKAGE, 0)
            true
        }.getOrDefault(false)

    // ── Commands ──────────────────────────────────────────────────────────

    /**
     * Show [effect] on the LEDs for [durationMs] milliseconds, then revert.
     * [colorLeft] and [colorRight] are ARGB-packed integers (e.g. Color.RED).
     * [intensity] is 0–255.
     */
    fun flash(
        context: Context,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 255,
        speed: Float = 0.5f,
        durationMs: Long = 2_000L,
        priority: Int = 50,
        requestId: String? = null,
    ) = send(context, BifrostApi.ACTION_DISPLAY) {
        putExtra(BifrostApi.EXTRA_EFFECT,      effect)
        putExtra(BifrostApi.EXTRA_COLOR,       colorLeft)
        putExtra(BifrostApi.EXTRA_COLOR_RIGHT, colorRight)
        putExtra(BifrostApi.EXTRA_INTENSITY,   intensity.coerceIn(0, 255))
        putExtra(BifrostApi.EXTRA_SPEED,       speed.coerceIn(0f, 1f))
        putExtra(BifrostApi.EXTRA_DURATION_MS, durationMs.coerceIn(1L, BifrostApi.MAX_DURATION_MS))
        putExtra(BifrostApi.EXTRA_PRIORITY,    priority.coerceIn(0, 100))
        requestId?.let { putExtra(BifrostApi.EXTRA_REQUEST_ID, it.take(64)) }
    }

    /**
     * Begin a persistent LED override. Call [clear] to end it.
     * Prefer [flash] with a duration where possible.
     */
    fun show(
        context: Context,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 255,
        speed: Float = 0.5f,
        priority: Int = 50,
        requestId: String? = null,
    ) = send(context, BifrostApi.ACTION_DISPLAY) {
        putExtra(BifrostApi.EXTRA_EFFECT,      effect)
        putExtra(BifrostApi.EXTRA_COLOR,       colorLeft)
        putExtra(BifrostApi.EXTRA_COLOR_RIGHT, colorRight)
        putExtra(BifrostApi.EXTRA_INTENSITY,   intensity.coerceIn(0, 255))
        putExtra(BifrostApi.EXTRA_SPEED,       speed.coerceIn(0f, 1f))
        putExtra(BifrostApi.EXTRA_INDEFINITE,  true)
        putExtra(BifrostApi.EXTRA_PRIORITY,    priority.coerceIn(0, 100))
        requestId?.let { putExtra(BifrostApi.EXTRA_REQUEST_ID, it.take(64)) }
    }

    /** End your app's current LED override. */
    fun clear(context: Context) =
        send(context, BifrostApi.ACTION_CLEAR)

    /**
     * Install a named preset into Bifrost's preset list.
     * The preset is tagged to your package and removed automatically if your app is uninstalled.
     */
    fun installPreset(
        context: Context,
        name: String,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 200,
        speed: Float = 0.5f,
        replaceIfExists: Boolean = true,
    ) = send(context, BifrostApi.ACTION_INSTALL_PROFILE) {
        putExtra(BifrostApi.EXTRA_PROFILE_NAME,              name)
        putExtra(BifrostApi.EXTRA_EFFECT,                    effect)
        putExtra(BifrostApi.EXTRA_COLOR,                     colorLeft)
        putExtra(BifrostApi.EXTRA_COLOR_RIGHT,               colorRight)
        putExtra(BifrostApi.EXTRA_INTENSITY,                 intensity.coerceIn(0, 255))
        putExtra(BifrostApi.EXTRA_SPEED,                     speed.coerceIn(0f, 1f))
        putExtra(BifrostApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, replaceIfExists)
    }

    /** Remove a preset your app previously installed. */
    fun uninstallPreset(context: Context, name: String) =
        send(context, BifrostApi.ACTION_UNINSTALL_PROFILE) {
            putExtra(BifrostApi.EXTRA_PROFILE_NAME, name)
        }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun send(context: Context, action: String, fill: Intent.() -> Unit = {}) {
        val intent = Intent(action).apply {
            setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
            putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
            fill()
        }
        runCatching { context.sendBroadcast(intent, BifrostApi.PERMISSION) }
            .onFailure { Log.w(TAG, "sendBroadcast failed", it) }
    }

    // Max duration constant exposed for callers
    private const val MAX_DURATION_MS = 600_000L
}
```

Usage:

```kotlin
// In a game: show wanted-level red for 5s
Bifrost.flash(this, effect = "STROBE", colorLeft = Color.RED, durationMs = 5_000L)

// On a menu: ambient purple
Bifrost.show(this, effect = "BREATH", colorLeft = Color.rgb(120, 0, 255))

// When leaving the menu
Bifrost.clear(this)

// Ship a branded preset
Bifrost.installPreset(this, name = "Cyber Neon", effect = "CHASE",
    colorLeft = Color.CYAN, colorRight = Color.MAGENTA)
```

---

## 12 — Java example

The API is equally usable from Java:

```java
public class MyActivity extends AppCompatActivity {

    private static final String BIFROST_PACKAGE = "com.moonbench.bifrost";
    private static final String BIFROST_RECEIVER = BIFROST_PACKAGE + ".external.ExternalApiReceiver";
    private static final String PERMISSION       = BIFROST_PACKAGE + ".permission.CONTROL_LEDS";
    private static final String ACTION_DISPLAY   = BIFROST_PACKAGE + ".api.ACTION_DISPLAY";
    private static final String ACTION_CLEAR     = BIFROST_PACKAGE + ".api.ACTION_CLEAR";

    @Override
    protected void onResume() {
        super.onResume();
        sendDisplay(Color.BLUE, "BREATH", 180, 5000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Intent clear = makeBifrostIntent(ACTION_CLEAR);
        sendBroadcast(clear, PERMISSION);
    }

    private void sendDisplay(int color, String effect, int intensity, long durationMs) {
        Intent intent = makeBifrostIntent(ACTION_DISPLAY);
        intent.putExtra("effect",       effect);
        intent.putExtra("color",        color);
        intent.putExtra("intensity",    intensity);
        intent.putExtra("durationMs",   durationMs);
        sendBroadcast(intent, PERMISSION);
    }

    private Intent makeBifrostIntent(String action) {
        Intent i = new Intent(action);
        i.setClassName(BIFROST_PACKAGE, BIFROST_RECEIVER);
        i.putExtra("apiVersion", 1);
        return i;
    }
}
```

---

## 13 — Recipes

### Health bar

```kotlin
// Call this whenever the player's HP changes (debounced, not every frame)
fun onHealthChanged(context: Context, hp: Int, maxHp: Int) {
    val ratio = hp.toFloat() / maxHp
    val color = when {
        ratio > 0.6f -> Color.GREEN
        ratio > 0.3f -> Color.YELLOW
        else         -> Color.RED
    }
    Bifrost.flash(context,
        effect    = "STATIC",
        colorLeft = color, colorRight = color,
        intensity = (ratio * 255).toInt().coerceIn(60, 255),
        durationMs = 500L,   // auto-expire after half a second so the next call wins cleanly
        priority  = 40)
}
```

### Low-battery alarm

```kotlin
fun onLowBattery(context: Context) {
    Bifrost.flash(context,
        effect    = "STROBE",
        colorLeft = Color.RED,
        durationMs = 8_000L,
        priority  = 90)        // high — don't let other apps stomp this
}
```

### Player-colour on multiplayer connect

```kotlin
val playerColors = listOf(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW)

fun onPlayerAssigned(context: Context, playerIndex: Int) {
    Bifrost.show(context,
        effect    = "BREATH",
        colorLeft = playerColors[playerIndex],
        priority  = 60)
}

fun onSessionEnd(context: Context) {
    Bifrost.clear(context)
}
```

### Ship a themed preset

```kotlin
// In Application.onCreate or first-launch flow
fun installBrandPresets(context: Context) {
    Bifrost.installPreset(context,
        name      = "Ember Glow",
        effect    = "PULSE",
        colorLeft = Color.rgb(255, 80, 0),
        colorRight = Color.rgb(200, 40, 0),
        intensity = 220,
        speed     = 0.4f)
}
```

---

## 14 — Common mistakes

### Command is silently dropped

Bifrost only processes commands when its foreground service is running. There is no way to start it remotely. If the user hasn't opened Bifrost or has swiped it away, your broadcasts vanish. Guard with `isInstalled` and design your integration to degrade gracefully when absent.

### `RESULT_REJECTED_DISABLED`

The user hasn't toggled "Allow third-party LED control" in Bifrost's settings. You may surface a friendly prompt ("Enable Bifrost integration in Bifrost › Settings for LED effects"), but respect their choice. Don't poll or re-prompt on every launch.

### `RESULT_REJECTED_RATE_LIMITED`

You're sending more than ~4 commands per second. Debounce game-state events before translating them to LED commands. A 200ms debounce window covers most rapid-state-change scenarios and stays well within the limit.

```kotlin
private val ledDebounce = Handler(Looper.getMainLooper())
private var ledRunnable: Runnable? = null

fun setLedColor(context: Context, color: Int) {
    ledRunnable?.let(ledDebounce::removeCallbacks)
    ledRunnable = Runnable { Bifrost.flash(context, colorLeft = color, durationMs = 300L) }
    ledDebounce.postDelayed(ledRunnable!!, 200)
}
```

### Forgetting to call `clear`

If you use `UNTIL_EXPLICIT_CLEAR` or `UNTIL_NEXT_COMMAND` (the default), an override that starts in `onResume` must be cleared in `onPause`. If you go to background without clearing, the LEDs stay on your effect until Bifrost is restarted.

### Not declaring `<queries>`

If you call `context.packageManager.getPackageInfo("com.moonbench.bifrost", 0)` to check availability without the `<queries>` block, it will always return false on Android 11+. Add:

```xml
<queries>
    <package android:name="com.moonbench.bifrost" />
</queries>
```

### Using `Color.TRANSPARENT` or `Color.BLACK` as "off"

Bifrost won't turn the LEDs off — it sets them to the colour you specify. `Color.TRANSPARENT` is `0x00000000`, which passes alpha 0 but the hardware ignores alpha. Use `ACTION_CLEAR` to revert to Bifrost's own state instead of trying to force colour 0.

---

## 15 — API versioning

Always include `EXTRA_API_VERSION = 1`. If a future Bifrost release introduces a breaking change, it will increment `API_VERSION` and reject older callers with `RESULT_REJECTED_VERSION`. This makes version skew immediately visible rather than silently producing wrong behaviour.

When targeting a new API version, check the Bifrost changelog for migration notes and update your constants. The stable contract is the string values in `BifrostApi` — the Kotlin object itself is just a convenient copy.
