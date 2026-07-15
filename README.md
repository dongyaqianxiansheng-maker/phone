# Call Recorder (Android 9 / API 28)

A compliant call-recording app built entirely on public Android APIs:
`TelephonyManager` / `PhoneStateListener` for call-state detection, and
`MediaRecorder` for capture. No hidden APIs, no root, no bypassing of
permission prompts.

## What it does

1. **Consent screen** (`ConsentActivity`) — shown once, before anything
   else. The user must tap "I Understand and Agree" before recording can
   ever start. **Replace the placeholder text in
   `res/values/strings.xml`** with your client's actual reviewed
   disclosure before shipping.
2. **Standard runtime permissions** (`MainActivity`) — `RECORD_AUDIO`,
   `READ_PHONE_STATE`, `READ_CALL_LOG`, `PROCESS_OUTGOING_CALLS`,
   requested via the normal system dialogs. If any is denied, recording
   does not start.
3. **Foreground service** (`CallRecorderService`) — listens for
   `CALL_STATE_OFFHOOK` to start recording and `CALL_STATE_IDLE` to stop
   it, and shows a persistent, non-dismissible notification the entire
   time it's running. This is what prevents silent/unnotified recording.

## Important limitations, not bugs

- **`MediaRecorder.AudioSource.VOICE_CALL`** is a public constant but
  many OEMs (especially post-2019 firmware) disable it at the hardware
  abstraction layer regardless of Android version, for privacy reasons.
  This code tries `VOICE_CALL` first and falls back to `MIC` if it
  fails, logging the fallback rather than pretending it captured both
  sides of the call. Test on your target device(s) — do not assume
  two-way audio is being captured just because the app doesn't crash.
- This approach is **Android 9–specific**. Android 10+ removed
  non-privileged call audio capture almost entirely; this code will not
  give you the same behavior on newer OS versions without a
  fundamentally different (and much more restricted) design.
- **Legal responsibility for call-recording consent laws** (one-party vs.
  all-party jurisdictions) sits with your client and the people they
  record — this app is a technical tool, not a legal compliance
  guarantee. The in-app consent screen and the signed disclaimer are
  both just part of the paper trail.

## Building locally

Requires Android SDK Platform 28 and Build-Tools 28.0.3 installed.

```
gradle assembleDebug
```

(No `gradlew` wrapper jar is checked in since it's a binary file. Run
`gradle wrapper --gradle-version 6.7.1` once locally if you want a
committed wrapper, or keep using the CI workflow as-is — it installs
Gradle via `gradle/actions/setup-gradle` instead of relying on a
wrapper.)

## CI

`.github/workflows/android.yml` builds `app-debug.apk` on every push to
`main` and uploads it as a workflow artifact.
