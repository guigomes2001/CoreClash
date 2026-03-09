# Core Clash Web Backend (Java + Firebase RTDB)

Backend Java recomendado para suportar o cliente web, com Firebase Realtime Database.

## Stack
- Java 17
- Spring Boot 3
- Firebase Admin SDK

## Configuração
1. Defina credenciais do service account no ambiente:
   - `GOOGLE_APPLICATION_CREDENTIALS=/caminho/serviceAccount.json`
2. Defina a URL do RTDB:
   - `FIREBASE_DATABASE_URL=https://<projeto>.firebaseio.com`

## Rodar
```bash
cd web-backend
./gradlew bootRun
```

## Endpoints
- `POST /api/matches` cria sala
- `GET /api/matches/{id}` busca estado
- `POST /api/matches/{id}/moves` aplica jogada

## Observação
Sim: Java é uma ótima escolha para backend nesse caso e integra bem com Firebase/RTDB via Admin SDK.
