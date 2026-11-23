# Spec 005 — Onboarding com contraste e insets

> **Status:** Aplicado
> **Atende:** Constituição D1–D9

## Problema

No POCO X7 Pro (edge-to-edge, `targetSdk` 36) as telas de portão, login do relay e pareamento LAN desenham título e seta sem `Surface`/`onBackground`. `LocalContentColor` padrão é preto sobre `ObsidianBg`. O cabeçalho entra debaixo da status bar. O texto de `HIVEMIND_SEED_EMAIL` fica na abertura da tela.

## Decisão

- `HiveMindTheme` envolve o conteúdo numa `Surface` com `background` e `contentColor = onBackground`.
- Cada estilo da tipografia declara `color = TextPrimary`.
- `android:forceDarkAllowed=false` para o HyperOS não inverter o tema escuro.
- Portão, login, pareamento, lista de IDEs e vínculo usam `statusBarsPadding` + `navigationBarsPadding` + `imePadding`.
- O texto de conta inicial do relay fica num bloco **Ajuda**, fechado.
- O app permanece só no tema Havrus escuro. Contraste no claro do sistema é garantido por não deixar o force-dark inverter o branco para preto.
