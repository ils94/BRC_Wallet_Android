
# BRC Wallet (Android)

A lightweight, non-custodial **BrowserCoin (BRC)** wallet for Android, written in Java.

This app lets you create or import an Ed25519 wallet, view your real on-chain balance, send BRC, scan QR codes, and keep a local transaction history — all while communicating with the official BrowserCoin helper API (or any other compatible helper API).

## Features

- **Create / Import wallet**  
  Generate a new Ed25519 keypair or import an existing 32-byte private key (hex).

- **Password-protected private key**  
  Private key is encrypted with AES-GCM using a key derived via PBKDF2 (100 000 iterations) and stored securely in SharedPreferences.

- **Real on-chain balance & nonce**  
  The wallet synchronizes the chain from a configurable height and calculates the exact balance and nonce by replaying relevant transactions.

- **Send BRC**  
  Builds the official 152-byte transaction (chain-id + from + to + amount + fee + nonce + Ed25519 signature) and submits it to the helper API.

- **QR Code support**  
  - Display your address as a QR code  
  - Scan recipient addresses with the camera

- **Transaction history**  
  Local history of sends, receives, mining rewards, locks and redeems.

- **Custom helper server**  
  Change the API base URL at any time (default: `https://api1.browsercoin.org`).

- **Secure screen**  
  `FLAG_SECURE` is enabled to prevent screenshots of the private key / wallet screen.

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection
- Camera permission (only for QR scanning)

## Building

1. Clone the repository:
   ```bash
   git clone https://github.com/ils94/BRC_Wallet_Android.git
   cd BRC_Wallet_Android
