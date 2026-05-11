# 🌊 SahyadriSiri — Water Quality Reporter for the Western Ghats

<p align="center">
  <strong>A production-grade, offline-first Android app for crowdsourced water quality monitoring in the Sahyadri (Western Ghats) region of India.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=flat-square&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-blue?style=flat-square&logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-purple?style=flat-square" />
  <img src="https://img.shields.io/badge/Backend-Supabase-3ECF8E?style=flat-square&logo=supabase" />
  <img src="https://img.shields.io/badge/Map-MapLibre_GL-orange?style=flat-square" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" />
</p>

---

## ✨ Features

| Feature | Details |
|---|---|
| 🗺️ **Interactive Map** | MapLibre GL Native with OpenStreetMap + ArcGIS satellite toggle |
| 📍 **GPS Reporting** | Tag water quality observations to your exact GPS location |
| 📸 **Photo Evidence** | Capture and attach compressed photos (auto-resized to 800px) |
| 🔬 **Water Metrics** | Record pH, turbidity, DO, temperature, and overall quality score |
| 🧠 **AI Assistant** | Gemini-powered chatbot for water quality advice |
| 🔔 **Smart Alerts** | Push notifications for pollution reports within 10km of your location |
| 🚶 **Walking Navigation** | OSRM-powered route to any report on the map |
| 🌐 **Offline-First** | Full offline support — reports sync automatically when connectivity returns |
| ⚡ **Realtime Sync** | Live updates via Supabase Realtime — see new reports appear instantly |
| 🔐 **Secure Auth** | Email + Google Sign-In via Supabase Auth |
| 🎨 **Liquid Glass UI** | Custom glassmorphism design system — no third-party UI libraries |

---

## 🏗️ Architecture

```
com.sahyadrisiri/
├── data/
│   ├── api/            SupabaseClient singleton (Auth, Postgrest, Realtime, Storage)
│   ├── local/          Room database (ReportDao, ReportEntity, AppDatabase)
│   ├── model/          Data classes (Report, PlaceResult, OsrmGeometry)
│   └── repository/     ReportRepository (offline-first CRUD, delta sync, place search)
├── service/
│   ├── SyncWorker.kt   Background sync via WorkManager (upsert strategy)
│   └── NotificationHelper.kt  Location-based 10km radius alert engine
├── ui/
│   ├── glass/          Liquid Glass design system (GlassPanel, StatusGlassCard, etc.)
│   ├── screens/        MapScreen, AlertsScreen, WikiScreen
│   ├── components/     ReportDetailSheet, AddReportSheet, ChatBotSheet
│   └── theme/          Color.kt, Theme.kt (Material3)
├── viewmodel/          MainViewModel (single source of truth, local-first StateFlow)
└── MainActivity.kt     Root composable, navigation, permissions
```

### Data Flow

```
User Action → ViewModel → Repository → Room (local) → UI updates instantly
                                     ↘ Supabase (cloud) — async, retries on failure
                                     
Supabase Realtime → ViewModel → Room (local) → UI updates instantly
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK 34**
- **Java 17**
- A [Supabase](https://supabase.com) project with the `reports` table
- A [Gemini API Key](https://aistudio.google.com/) (for AI chatbot)

### 1. Clone the Repository

```bash
git clone https://github.com/RakeshAM10/SahyadriSiri.git
cd SahyadriSiri
```

### 2. Configure API Keys

```bash
cp local.properties.example local.properties
```

Open `local.properties` and fill in your values:

```properties
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
GEMINI_API_KEY=your_gemini_api_key_here
```

> ⚠️ **Never commit `local.properties`** — it is already in `.gitignore`.

### 3. Supabase Setup

Create a `reports` table in your Supabase project with these columns:

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | Primary key, default `gen_random_uuid()` |
| `name` | `text` | Reporter's name |
| `latitude` | `float8` | GPS latitude |
| `longitude` | `float8` | GPS longitude |
| `status` | `text` | `clean`, `warning`, or `polluted` |
| `ph` | `float8` | pH level (0–14) |
| `turbidity` | `float8` | NTU units |
| `dissolved_oxygen` | `float8` | mg/L |
| `temperature` | `float8` | °C |
| `description` | `text` | User notes |
| `photo` | `text` | Base64-encoded JPEG |
| `location_name` | `text` | Reverse-geocoded place name |
| `timestamp` | `text` | ISO 8601 string |
| `user_id` | `uuid` | FK to `auth.users` |

Enable **Row Level Security (RLS)** with policies for `SELECT`, `INSERT`, `UPDATE`, `DELETE` restricted to authenticated users.

### 4. Build & Run

Open the project in Android Studio and press **Run ▶️**.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Map** | MapLibre GL Native (OpenStreetMap + ArcGIS Satellite) |
| **Backend** | Supabase (Auth, Postgrest, Realtime, Storage) |
| **Local DB** | Room (offline cache with pending sync flags) |
| **Background** | WorkManager (exponential backoff sync) |
| **Networking** | Ktor (Supabase SDK) + Retrofit/OkHttp (Nominatim, OSRM) |
| **AI** | Google Gemini 1.5 Flash via REST |
| **Auth** | Supabase Auth (Email + Google Sign-In) |
| **Location** | Google Play Services FusedLocationProvider |

---

## 🔒 Security

- API keys are loaded from `local.properties` (excluded from Git)
- Supabase RLS policies enforce per-user data access
- JWT-based authentication for all API calls
- Photo compression (800px max, JPEG 60%) prevents data exfiltration via oversized payloads

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ for the Western Ghats 🌿
</p>
