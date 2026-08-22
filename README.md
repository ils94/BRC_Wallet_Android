
# BRC Wallet (Android)

A lightweight, non-custodial **BrowserCoin (BRC)** wallet for Android, written in pure Java.

This app lets you create or import an Ed25519 wallet, view your real on-chain balance, send BRC, scan QR codes, and keep a local transaction history — all while communicating with the official BrowserCoin helper API (or any other compatible helper API).

## Features

- **Create / Import wallet**  
  Generate a new Ed25519 keypair or import an existing wallet using either:
  - 32-byte private key (hex)
  - 24-word BIP39 seed phrase

- **BIP39 seed phrase support**  
  Your 32-byte private key is treated as entropy and converted into a valid 24-word BIP39 mnemonic (and vice-versa) using the `bitcoinj` library. This allows you to back up your wallet as a seed phrase instead of a long hex string.

- **Password-protected private key**  
  The private key is encrypted with **AES-GCM** using a key derived via **PBKDF2** (100,000 iterations) and stored in `SharedPreferences` (private mode).

- **Full-node style balance & history**  
  The wallet synchronizes the chain from a configurable height and calculates the exact balance and nonce by replaying relevant transactions.

- **Send BRC**  
  Builds the official 152-byte transaction (`chain-id + from + to + amount + fee + nonce + Ed25519 signature`) and submits it to the helper API.

- **QR Code support**
  - Display your address as a QR code
  - Scan recipient addresses with the camera

- **Transaction history**  
  Local history of sends, receives, mining rewards, locks and redeems.

- **Background sync service**  
  A foreground service (started manually via menu) syncs the blockchain every 60 seconds and notifies you of incoming transactions.

- **Custom helper server**  
  Change the API base URL at any time (default: `https://api1.browsercoin.org`).

- **Secure screen**  
  `FLAG_SECURE` is enabled on the main and export screens to prevent screenshots.

## Requirements

- Android 8.0 (API 26) or higher
- Internet connection
- Camera permission (only for QR scanning)
- Notification permission (for background sync notifications)
- Foreground service permission (for background blockchain sync)

## Permissions

The app declares the following permissions in `AndroidManifest.xml`:
- `INTERNET` — API communication
- `ACCESS_NETWORK_STATE` — network state checks
- `CAMERA` — QR code scanning
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC` — background blockchain sync
- `POST_NOTIFICATIONS` — incoming transaction alerts (Android 13+)

## Building

1. Clone the repository:
   ```bash
   git clone https://github.com/ils94/BRC_Wallet_Android.git
   cd BRC_Wallet_Android
