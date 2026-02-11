# 🛡️ COLOCK (Call Of LOCK)
### Integrated Security Solution for Smishing, Voice Phishing, and Impersonation

COLOCK is an advanced mobile security application designed to protect users from financial fraud using real-time AI analysis and proactive monitoring.

## 🚀 Key Features

### 1. Smishing Guard
*   **Real-time Link Analysis:** Automatically intercepts and analyzes URLs in incoming SMS messages.
*   **Safe Browsing Integration:** Leverages Google's global threat database to identify malicious domains.
*   **Heuristic Detection:** Multi-layered analysis including TLD check, brand typosquatting detection, and SSL verification.

### 2. Voice Guard
*   **AI Context Analysis:** Analyzes live call audio using Google Gemini 2.5 Flash to detect psychological manipulation.
*   **Real-time Warnings:** Alerts users instantly when suspicious keywords or patterns (e.g., impersonating officials) are detected.

### 3. Anti-Impersonation Service
*   **Mutual Verification:** Allows users to request identity confirmation from family or acquaintances via the app.
*   **Secure Infrastructure:** Built on Firebase Cloud Firestore and FCM for reliable, encrypted request handling.

### 4. Data Leak Protection
*   **Usage Monitoring:** Detects unusual background data usage that may indicate unauthorized data exfiltration.

## 🛠 Tech Stack
*   **Platform:** Android (Kotlin)
*   **UI Framework:** Jetpack Compose & XML View System
*   **AI/ML:** Google Gemini 2.5 Flash, TensorFlow Lite
*   **Backend:** Firebase (Auth, Firestore, Messaging)
*   **Database:** Room Persistence Library
*   **Networking:** Retrofit2, OkHttp3

## ⚙️ Configuration
1.  Add your `google-services.json` to the `app/` directory.
2.  Provide API keys in the source code where indicated (`YOUR_API_KEY`).

---
© 2026 KMOU Capstone Team
