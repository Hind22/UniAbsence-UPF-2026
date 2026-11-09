# UniAbsence

## Overview
UniAbsence is a mobile application for managing university absences. It consists of three main parts:

- **Frontend**: HTML/CSS/JavaScript built with Vite.
- **Backend**: Java Spring Boot exposing REST APIs and persisting data in SQLite.
- **Mobile**: Android build using Capacitor, which wraps the web frontend.

The repository follows the structure described in the project proposal.

## Project Structure
```
UniAbsence/
├─ backend/          # Spring Boot backend
│   └─ pom.xml
├─ mobile/           # Vite frontend + Capacitor configuration
│   ├─ capacitor.config.ts
│   ├─ package.json
│   └─ src/          # Vite source files (index.html, styles, scripts)
├─ docs/             # Documentation
│   └─ README.md
└─ README.md         # This file
```

## Getting Started
1. **Backend**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
2. **Frontend**
   ```bash
   cd mobile
   npm install   # install Vite and development dependencies
   npm run dev   # start dev server
   ```
3. **Capacitor (Android)** – after the frontend builds, run:
   ```bash
   npx cap add android
   npx cap sync android
   ```
   (Android project generation is deferred until you are ready.)

## Prerequisites
- **Node.js** (v26.x) and **npm** (v11.x) – already verified.
- **Java 17** and **Maven** for the backend.
- **Android SDK** (optional, for later Android build).

Feel free to explore the `docs/` folder for more detailed design notes.
