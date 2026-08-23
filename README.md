
# BRC Wallet (Android)

A lightweight, non-custodial **BrowserCoin (BRC)** wallet for Android, written in pure Java.

This app lets you create or import an Ed25519 wallet, view your real on-chain balance, send BRC, scan QR codes, keep a local transaction history, manage contacts, and stay in sync with your account transactions.
## Features

### Wallet Management

- **Create / Import wallet**  
  Generate a new Ed25519 keypair or import an existing wallet using either:
  - 32-byte private key (hex)
  - 24-word BIP39 seed phrase

- **BIP39 seed phrase support**  
  Your 32-byte private key is treated as entropy and converted into a valid 24-word BIP39 mnemonic (and vice-versa) using the `bitcoinj` library. This allows you to back up your wallet as a seed phrase instead of a long hex string.

- **Password-protected private key**  
  The private key is encrypted with **AES-GCM** using a key derived via **PBKDF2** (100,000 iterations) and stored in `SharedPreferences` (private mode).

- **Confirmation timers**  
  Destructive actions (creating/importing a wallet when one already exists) require a 10-second hold-to-confirm step to prevent accidental loss.

### Blockchain Sync & Transactions

- **Full-node style balance & history**  
  The wallet synchronizes the chain from a configurable height and calculates the exact balance and nonce by replaying relevant transactions.

- **Send BRC**  
  Builds the official 152-byte transaction (`chain-id + from + to + amount + fee + nonce + Ed25519 signature`) and submits it to the helper API. Custom transaction fees are supported.

- **QR Code support**
  - Display your address as a QR code
  - Scan recipient addresses with the camera

- **Transaction history**  
  Local history of sends, receives with:
  - **TxID** (SHA-256 of the raw transaction bytes)
  - Filter by type (sent, received, all)
  - Sort by block height or value (ascending/descending)
  - Clickable links to an external blockchain explorer (Tabscope by default) for block, tx, and addresses

### Contacts

- **Contact list**  
  Save frequently used addresses with a name.

- **Add / Edit / Delete contacts**  
  Manage your contact list easily. Duplicate names or addresses are prevented.

- **Send to contact**  
  Pre-fill the send dialog with a contact's address.

- **Import / Export contacts**  
  Export all contacts to a JSON file using Android's Storage Access Framework (SAF). Import contacts from a JSON file.

- **Contact recognition in history**  
  In the transaction history, if a sender/receiver address matches a saved contact, the contact name is displayed instead of the raw address. Your own wallet address is shown as "My Address".

- **Contact search filter**  
  Filter the transaction history by typing a contact name (with autocomplete suggestions).

### Background Service

- **Automatic sync service**  
  A foreground service (started manually via menu) syncs the blockchain every 60 seconds and notifies you of incoming BRC transactions.

- **Resilient to failures**  
  The service keeps retrying on errors with exponential backoff. It supports **multiple helper servers** (comma-separated) and automatically fails over to the next if one fails.

### Server Configuration

- **Custom helper server(s)**  
  Change the API base URL at any time. You can specify multiple servers separated by commas for automatic failover (e.g., `https://api1.browsercoin.org,https://api2.browsercoin.org`).

### Security

- **Secure screen**  
  `FLAG_SECURE` is enabled on the main and export screens to prevent screenshots.

- **Encrypted storage**  
  Private keys are encrypted at rest with AES-GCM.

### Internationalization

- **Multi-language support**  
  English, Português (Brasil), Deutsch, Español, Français.

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
