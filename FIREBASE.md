# Backend Firebase — gesAbsence (Module 5)

Firestore + Cloud Functions. Le client Android n'écrit **jamais** directement
dans Firestore : tout passe par des fonctions *callable* (Admin SDK).

## Modèle de données Firestore

| Collection | Doc id | Champs |
|---|---|---|
| `students` | `studentId` (matricule) | `fullName`, `classId`, `parentContacts[]` (`{type: fcm\|sms\|email, value}`), `parentUid?` |
| `classes` | `classId` | `name`, `teacherIds[]` |
| `sessions` | **le code BLE** (8 hex, généré par le téléphone du prof) | `classId`, `teacherId`, `label?`, `status` (`active\|closed`), `openedAt`, `closedAt?`, `autoCloseAt?`, `counts?` |
| `presences` | `${sessionId}_${studentId}` | `sessionId`, `studentId`, `classId`, `status` (`present\|absent`), `confirmedAt?`, `recordedAt`, `source`, `rssi?` |

Le doc id déterministe de `presences` = la clé anti-doublon côté client
(`session::student`) → un second pointage pour la même paire est impossible.

## Cloud Functions (`functions/src/`, région `europe-west1`)

| Fonction | Type | Rôle |
|---|---|---|
| `openSession` | callable | Le prof démarre l'appel → crée/réactive `sessions/{code}` (`status: active`, `autoCloseAt` optionnel). Idempotent. |
| `confirmPresence` | callable | Valide un pointage dans une **transaction** : session existante + `active` + non expirée, puis crée `presences/{id}` avec `status: present`. Doublon → `{status: "already"}`. Erreurs : `invalid-argument`, `not-found`, `failed-precondition`. |
| `closeSession` | callable | Passe la session à `closed`, lit le *roster* (`students where classId ==`) et les présents, écrit un doc `status: absent` pour chaque élève non pointé (batchs de 450), stocke `counts`. Renvoie `{present, absent, absentStudentIds}`. Rejouable. |
| `autoCloseExpiredSessions` | scheduled (5 min) | Clôture les sessions `active` dont `autoCloseAt <= now` via la même logique. |

`closeSession` expose `absentStudentIds` — point d'accroche du **Module 6** (FCM parents).

## Prérequis de déploiement

1. Créer un projet Firebase, puis fixer son id dans [`.firebaserc`](.firebaserc)
   (actuellement `gesabsence-poc`) ou `firebase use --add`.
2. Plan **Blaze** requis (Cloud Functions v2).
3. App Android : télécharger `google-services.json` depuis la console Firebase
   et le placer dans `app/`. Tant qu'il est absent, le plugin Google Services
   n'est pas appliqué et l'app tourne **hors-ligne** sur `LocalPresenceRepository`
   (l'anti-doublon local reste actif, les pointages s'accumulent dans l'outbox).

## Commandes

```bash
cd functions
npm install
npm run build         # tsc -> lib/
npm run lint
npm run serve         # émulateurs Functions + Firestore

# déploiement (depuis la racine)
firebase deploy --only firestore:rules,firestore:indexes
firebase deploy --only functions
```

## Règles de sécurité

[`firestore.rules`](firestore.rules) : lecture réservée aux utilisateurs
authentifiés (tableau de bord, Module 7), **aucune écriture client** — seules
les Functions (Admin SDK) écrivent.

## Intégration Android

| Élément | Fichier |
|---|---|
| Appel `confirmPresence` | `data/PresenceBackend.kt` (`FirebaseFunctionsPresenceBackend`) |
| Appels `openSession` / `closeSession` | `data/SessionBackend.kt` |
| Offline-first + outbox + retry | `data/SyncingPresenceRepository.kt` (`flushOutbox()`) |
| Sélection backend réel vs no-op | `PresenceBackendFactory` / `SessionBackendFactory` (selon `FirebaseApp.getApps()`) |

`StudentViewModel` écrit toujours en local puis pousse au backend si disponible ;
`TeacherViewModel` appelle `openSession` au démarrage de la diffusion et
`closeSession` à l'arrêt (nécessite d'avoir renseigné *professeur* + *classe*).
