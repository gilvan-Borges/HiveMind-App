# Spec 001 — Paleta Havrus Obsidian & Brand Gold no HiveMind-App

> **Status:** Aplicado
> **Atende:** Constituição D1–D8 (`.specify/memory/constitution.md`)

## Tokens (`Theme.kt` e `colors.xml`)

- Fundo (`ObsidianBg`): `#050505`
- Painel (`SurfacePanel`): `#0C0C0C`
- Campo (`SurfaceInput`): `#0A0A0A`
- Cartão sólido (`ObsidianCard`): `#111111`
- Hover (`SurfaceHover`): `#1B1B1B`
- Elevado (`ObsidianSurface`): `#1F1F1F`
- Opção ativa (`SurfaceOptionActive`): `#353535`
- Borda divisória (`ObsidianBorder`): `#262626`
- Ação primária (`ActionPrimary` / `ActionPrimaryText`): `#F3F2F0` / `#090909`
- Destaque (`BrandGold`, alias `SteelBlue`): `#C6A15B`
- Foco (`BrandGoldStrong`): `#E8C881`
- Texto sobre o destaque (`OnPrimary`): `#090909` (contraste 8.2:1 sobre `#C6A15B`)
- Textos: `TextPrimary = #FFFFFF`, `TextBody = #E5E5E5`, `TextDim = #A3A3A3`, `TextTertiary = #8A8A8A`
- Sinais: `SuccessGreen = #10B981`, `ErrorRed = #E58C98`, `ApprovalAmber = #E2AE62`, `InfoBlue = #3B82F6`

`darkColorScheme` define cada papel (incluindo `primaryContainer`, `inversePrimary` e `surfaceTint`). O padrão violeta do Material 3 não permanece. `#C6A15B` e `#E8C881` sobre os fundos `#050505` / `#0C0C0C` / `#111111` / `#1F1F1F` ficam acima de 4.5:1. `#8C6E33` é só decorativo: não entra em texto.

## Launcher e splash

- Ícone adaptativo: fundo `#050505`; foreground é o símbolo de `assets/brand/hivemind-icon.png` dentro da zona segura.
- Monochrome (API 33) para ícones temáticos: traço do hexágono e do cérebro em rede.
- Mipmaps legados `mdpi` … `xxxhdpi` com o mesmo símbolo sobre `#050505`.
- Splash (`Theme.HiveMindMobile.Splash`) usa o mesmo símbolo sobre `#050505`.
- O vetor antigo do hexágono azul sai do launcher e da notificação.
