# gesAbsence — gestion dématérialisée des présences (BLE)

POC Android (Kotlin / Jetpack Compose / MVVM) + backend Firebase.

Le professeur diffuse un `session_id` en **BLE advertising** ; chaque élève le
détecte depuis sa place (**scan BLE + seuil RSSI**) et confirme sa présence en un
geste. Le backend enregistre les pointages, bloque les doublons, et marque
absents les élèves non pointés à la clôture. Les parents sont notifiés (FCM).
Aucune étape n'exige de déplacement physique en classe.

## Modules

| # | Module | État |
|---|--------|------|
| 1 | Structure du projet Kotlin (2 modes, Compose, MVVM, permissions BLE) | ✅ |
| 2 | BLE côté professeur — advertising | ⬜ |
| 3 | BLE côté élève — scan + RSSI + notification | ⬜ |
| 4 | Confirmation de présence + anti-doublon client | ⬜ |
| 5 | Backend Firebase (Firestore + Cloud Functions) | ⬜ |
| 6 | Notifications parents (FCM) | ⬜ |
| 7 | Tableau de bord web + export CSV | ⬜ |

## Prérequis

- JDK 17+
- Android SDK (compileSdk 35), un appareil **physique** avec BLE
  (l'émulateur ne supporte pas l'advertising BLE)
- `google-services.json` dans `app/` (Module 5) — tant qu'il est absent, le
  plugin Google Services n'est pas appliqué et le build reste fonctionnel.

## Build

```bash
./gradlew assembleDebug        # APK debug
./gradlew testDebugUnitTest    # tests unitaires JVM
./gradlew installDebug         # installe sur l'appareil branché
```

## Architecture (Module 1)

```
sn.uadb.gesabscence
├─ MainActivity                 point d'entrée Compose
├─ GesAbsenceApp                Application + canaux de notification
├─ navigation/AppNavigation     routage par rôle persisté
├─ data/RolePreferences         DataStore : rôle, student_id, seuil RSSI
├─ ble/BlePermissions           permissions runtime par version d'Android
├─ ui/
│  ├─ AppViewModel              état du rôle (AndroidViewModel)
│  ├─ PermissionGate            garde de permissions réutilisable
│  ├─ role/RoleSelectionScreen  choix Professeur / Élève
│  ├─ teacher/                  TeacherScreen + TeacherViewModel
│  └─ student/                  StudentScreen + StudentViewModel
└─ util/SessionId               génération d'UUID court (4 octets)
```

Les `ViewModel` exposent un `StateFlow<…UiState>` ; les points d'accroche BLE et
backend sont marqués `TODO(Module N)`.
