# Skill Registry — JavaMensajeria
<!-- Updated: 2026-06-04 -->

## Project Conventions

- **Language**: Java 21 (server + client), Python 3.x (ML service)
- **Build**: Maven Shade Plugin (servidor), JavaFX Maven Plugin (cliente), pip (Python)
- **Architecture**: Hexagonal/Clean — layers: aplicacion / dominio / infraestructura / rest
- **Domain pattern**: Command (one Handler class per message type)
- **Run server**: `java -Dserver.port=9090 -jar target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar`
- **No project-level CLAUDE.md** — global at C:\Users\angel\.claude\CLAUDE.md

## Available Skills (User-level)

| Skill | Trigger |
|-------|---------|
| `branch-pr` | Creating a pull request or preparing changes for review |
| `issue-creation` | Creating a GitHub issue, reporting a bug, requesting a feature |
| `judgment-day` | "judgment day", adversarial review, "dual review", "doble review", "juzgar" |
| `skill-creator` | Creating new AI skills or documenting patterns for AI |

## Compact Rules

### branch-pr
- Issue-first: always create an issue before opening a PR
- PR title: under 70 chars, imperative mood
- Body: Summary bullets + Test plan checklist

### issue-creation
- Title: imperative mood, specific
- Body: context, acceptance criteria, technical notes

### judgment-day
- Launch two independent judge sub-agents simultaneously (blind to each other)
- Each reviews the same target from a fresh context
- Synthesize, fix, re-judge; escalate after 2 failed iterations

## SDD Persistence Mode

**Mode**: `engram`
No `openspec/` directory. All SDD artifacts persisted to Engram under project `javamensajeria`.

## Project Modules

| Module | Path | Stack |
|--------|------|-------|
| Mensajeria (shared protocol) | `SERVIDOR/JavaMensajeriaComunicacion/` | Java 21 Maven |
| JavaMensajeriaServidor | `SERVIDOR/JavaMensajeriaServidor/` | Java 21, Spring Boot 3.4.1, Hibernate 6.6, MySQL, TCP/UDP |
| cliente-javafx | `CLIENTE/` | Java 21, JavaFX 21, H2 DB |
| ML Genre Classifier | `IdentificacionGenerosMusicales/backend/` | Python 3, FastAPI 0.110, TensorFlow 2.20, librosa |

## Testing Status

- **Servidor**: ❌ No JUnit, no test sources
- **Cliente**: ⚠️ JUnit Jupiter 5.10.1 configured, zero test files written
- **Python**: ❌ No pytest

**Strict TDD Mode**: DISABLED (no test runner in primary server module)
