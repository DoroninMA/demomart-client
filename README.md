# DemoMart customer app

Loyalty card app for Android. The customer signs in, sees the points balance and history, and shows the card at the register as a rotating QR code or by tapping the phone (NFC card emulation). The card can also be added to Google Wallet.

Kotlin, Jetpack Compose, Hilt, Retrofit, DataStore, Firebase Auth + FCM, ZXing. Android 8.0+.

## Build

JDK 17+, Android SDK 37.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Run against a local server

Start `demomart-server` with `AUTH_MODE=fake` and seed it. The debug build talks to `http://10.0.2.2:8000/` (emulator). For a phone, either use `adb reverse tcp:8000 tcp:8000` with `loyalty.serverUrl=http://127.0.0.1:8000/`, or point it at the PC's LAN address.

In fake mode the login screen offers the demo accounts: Anna (1355 points), Ben (240) and Chloe (0). **Create account** makes an empty one.

## Configuration (`local.properties`)

| Key | |
|---|---|
| `loyalty.serverUrl` | debug server URL |
| `loyalty.releaseServerUrl` | release server URL, must be https |
| `loyalty.releaseWalletEnabled` | `true` shows the Google Wallet button in release |

`-Ployalty.authMode=firebase` switches a debug build to Firebase; it needs `app/google-services.json` with the SHA-1 of your signing key (`.\gradlew.bat :app:signingReport`). Release builds always use Firebase.

## Card token

QR and NFC carry the same token, generated offline: `DM1:<card number>:<TOTP>`, 30 s step, so the card works without network.

Over NFC the app answers SELECT for AID `F0444D4C4F5901` with the token and `9000`. If nobody is signed in it returns `6985`.

## Manual check

With the register on a second phone and the seeded server:

- Anna, €23.00, redeem 500 points → €18.00 paid, +18 points, 873 left. Refund it → back to 1355.
- Same over NFC, also with the customer phone in airplane mode and with the app killed (`adb shell am kill com.demomart.client`).
- Signed out → the register shows "Customer not signed in".
- Home open while a sale goes through → the balance animates once.

## License

MIT, see `LICENSE`.
