---
name: hivemind-design-sdd
description: Roteiro SDD obrigatório antes de criar ou alterar telas, componentes ou tema no HiveMind-App (Jetpack Compose).
---

# HiveMind-App Design SDD

1. Leia `.specify/memory/constitution.md` (Artigos D1–D8).
2. Toda tela ou alteração visual relevante começa por uma spec em `.specify/specs/NNN-nome/spec.md`.
3. Use apenas os tokens de `dev.hivemind.mobile.theme` (`Theme.kt` + `MaterialTheme.colorScheme`) — nunca cores literais soltas em telas Compose.
4. Garanta alvos de toque >= 44 dp, os 4 estados (carregando, erro, vazio, dados) e contraste WCAG >= 4.5:1.
