# Skill Registry — xd
<!-- Generated: 2026-06-04 -->

## Project Conventions

- **Language**: Java 21 (server + client), Python 3.x (ML service)
- **Build**: Maven (Java), pip (Python)
- **Architecture**: Hexagonal (server), DDD-layered (client)
- **IDE**: IntelliJ IDEA
- **No project-level AGENTS.md, CLAUDE.md, or .cursorrules found**

## Available Skills (User-level)

| Skill | Trigger |
|-------|---------|
| `branch-pr` | Creating a pull request or preparing changes for review |
| `issue-creation` | Creating a GitHub issue, reporting a bug, requesting a feature |
| `go-testing` | Writing Go tests, using teatest, adding test coverage |
| `judgment-day` | "judgment day", adversarial review, dual review |
| `skill-creator` | Creating new AI skills or documenting patterns for AI |
| `skill-registry` | "update skills", "skill registry", after installing/removing skills |
| `sdd-init` | "sdd init", initialize SDD, "openspec init" |
| `sdd-propose` | Creating or updating a change proposal |
| `sdd-explore` | Exploring ideas or investigating the codebase |
| `sdd-spec` | Writing or updating specs for a change |
| `sdd-design` | Writing or updating technical design for a change |
| `sdd-tasks` | Breaking down a change into implementation tasks |
| `sdd-apply` | Implementing tasks from a change |
| `sdd-verify` | Validating implementation against specs |
| `sdd-archive` | Archiving a completed change |
| `sdd-onboard` | Walking through the full SDD workflow |

## SDD Persistence Mode

**Mode**: `engram`
No `openspec/` directory. All SDD artifacts persisted to Engram.

## Project Modules

| Module | Path | Stack |
|--------|------|-------|
| Mensajeria (shared protocol) | `SERVIDOR/JavaMensajeriaComunicacion/` | Java 21 Maven |
| JavaMensajeriaServidor | `SERVIDOR/JavaMensajeriaServidor/` | Java 21 Maven + Hibernate + MySQL |
| cliente-javafx | `CLIENTE/` | Java 21 Maven + JavaFX 21 |
| ML Genre Classifier | `IdentificacionGenerosMusicales/` | Python + FastAPI + TensorFlow |
