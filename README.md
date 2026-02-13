# CrossNote

CrossNote ist eine plattformübergreifende, Offline-First Notiz-Anwendung für Desktop-Systeme (Windows/Linux) mit geplanter Erweiterung auf mobile Plattformen.

Ziel des Projekts ist die Entwicklung einer ressourcenschonenden, lokal nutzbaren und modular aufgebauten Notizverwaltung, die vollständig offline funktioniert und perspektivisch eine optionale Synchronisation zwischen mehreren Geräten ermöglicht.

Das Projekt wurde im Rahmen eines Software-Engineering-Moduls entwickelt und legt besonderen Fokus auf Architekturqualität, saubere Schichtentrennung und nachhaltige Erweiterbarkeit.

---

# Projektübersicht

CrossNote verfolgt folgende Kernprinzipien:

- **Offline-First Architektur**  
  Alle Kernfunktionen funktionieren ohne Internetverbindung.

- **Saubere Schichtenstruktur**  
  Orientierung an Clean Architecture und Domain-Driven Design.

- **Modularität durch Gradle Multi-Module-Projekt**  
  Klare Trennung von Domain, Application, Infrastruktur und UI.

- **Erweiterbarkeit**  
  Architektur ist auf Synchronisation und mobile Clients vorbereitet.

- **Testbarkeit und Wartbarkeit**  
  Abhängigkeiten zeigen ausschließlich nach innen (Dependency Rule).

Die Anwendung richtet sich an Nutzerinnen und Nutzer, die ihre Notizen lokal verwalten möchten, ohne von Cloud-Diensten abhängig zu sein.

---

# Funktionsumfang

## Implementierte Kernfunktionen

- Notizen erstellen, bearbeiten und löschen
- Strukturierung über Notizbücher (hierarchische Organisation)
- Papierkorb mit Wiederherstellungsfunktion
- Revisionshistorie für Notizen
- Suchfunktion
- Lokale Speicherung mittels SQLite
- Desktop-Oberfläche auf Basis von JavaFX

## Geplante / Erweiterbare Funktionen

- Geräteübergreifende Synchronisation
- Konfliktauflösungsmechanismen
- Import/Export (z. B. Markdown, JSON)
- Android-Client
- Erweiterte Sicherheitsmechanismen

---

# Systemarchitektur

CrossNote ist als Gradle-Multi-Module-Projekt aufgebaut und folgt einer klaren Schichtenarchitektur.

```
ui-desktop → core-application → core-domain ← infra-persistence
```

## Architekturprinzipien

- **Dependency Rule:**  
  Abhängigkeiten zeigen ausschließlich nach innen.

- **Ports-and-Adapters (Hexagonales Muster):**  
  Infrastruktur implementiert ausschließlich Interfaces der Domain.

- **Trennung von Verantwortung:**  
  Jede Schicht erfüllt eine klar definierte Aufgabe.

## Modulübersicht

### core-domain
Enthält:
- Entitäten (Note, Notebook, Revision)
- Value Objects
- Repository-Interfaces
- Domänenlogik

Keine Abhängigkeit zu Frameworks oder technischen Details.

### core-application
Enthält:
- Use-Case-Implementierungen
- Application-Services
- Orchestrierung der Geschäftslogik

Abhängig ausschließlich von `core-domain`.

### infra-persistence
Enthält:
- SQLite-Implementierungen der Repository-Interfaces
- InMemory-Implementierungen für Testzwecke

Abhängig von `core-domain`.

### ui-desktop
Enthält:
- JavaFX-Benutzeroberfläche
- Controller
- Presenter
- UI-Zustandsverwaltung

Abhängig von `core-application`.

---

# Technische Details

- **Programmiersprache:** Kotlin (JVM)
- **Build-System:** Gradle (Wrapper enthalten)
- **UI-Technologie:** JavaFX
- **Persistenz:** SQLite (lokale Datenbank)
- **Architektur:** Multi-Module, Clean Architecture orientiert

---

# Voraussetzungen

- Java JDK 17 oder höher
- Windows, Linux oder macOS
- Keine separate Gradle-Installation notwendig

---

# Projekt starten

## Windows (PowerShell)

```powershell
.\gradlew :ui-desktop:run --no-configuration-cache
```

## Linux / macOS

```bash
./gradlew :ui-desktop:run --no-configuration-cache
```

---

# Build & Tests

Projekt bauen:

```bash
./gradlew build
```

Tests ausführen:

```bash
./gradlew test
```

---

# Datenhaltung

- Lokale Speicherung mittels SQLite
- Datenbank wird automatisch erzeugt
- Vollständige Offline-Funktionalität
- Keine Cloud-Anbindung erforderlich

---

# Dokumentation

Im Ordner `documentation/` befinden sich unter anderem:

- Projektskizze
- Use Cases
- Featurebeschreibung
- Softwarearchitektur
- Softwaredesign-Dokument
- Review-Dokumente
- Aufwandsschätzung
- Vorgehensmodell
- Verlaufsprotokoll

Diese Dokumente beschreiben Anforderungen, Architekturentscheidungen, Qualitätsziele und Entwicklungsprozess im Detail.

---

# Entwicklungsprozess

Das Projekt wurde nach einem agilen Vorgehensmodell (Scrum-orientiert) entwickelt.

Berücksichtigt wurden:

- Iterative Entwicklung
- Regelmäßige Code-Reviews
- Parallele Dokumentation
- Definition funktionaler und nicht-funktionaler Anforderungen
- Architekturentscheidungen mit Begründung

---

# Qualitätsziele

- Offline-Verfügbarkeit
- Performante lokale Datenverarbeitung
- Klare Modultrennung
- Wartbarkeit
- Erweiterbarkeit
- Strukturierte Fehlerbehandlung

---

# Projektteam

- Gerhard Uebe
- Nicolas Kauer
- Jakob Scheuermeyer
- Kai Seitz

---

# Repository

GitHub Repository:  
https://github.com/GamiXLP/CrossNote

---

# Lizenz

Das Projekt ist als Open-Source-Anwendung konzipiert.  
Eine konkrete Lizenz kann bei öffentlicher Veröffentlichung ergänzt werden.
