# Constituição de Design System (SDD) — HiveMind-App

> Versão 1.0.0 · Espelho oficial da Constituição SDD do repositório `gilvan-Borges/hivemind` (`.specify/memory/constitution.md`).
> Base comparativa: estrutura SDD rastreável (`Vibetex/ipg` `.specify`) + fundação visual e tokens por papel (`Havruss/havrus`).

## Artigos (D1–D8)
- **D1. Tokens por papel:** toda cor nasce em `Theme.kt` e `colors.xml` (nunca `Color(0xFF...)` solto em telas Compose novas).
- **D2. Separação de camadas (Havrus Obsidian & Brand Gold):**
  - Fundo (`#050505` — `ObsidianBg`)
  - Painel (`#0C0C0C` — `SurfacePanel`)
  - Cartão sólido (`#111111` — `ObsidianCard`)
  - Elevado (`#1F1F1F` — `ObsidianSurface`)
  - Campo (`#0A0A0A` — `SurfaceInput`)
- **D3. Contraste mínimo WCAG 2.2 AA (>= 4.5:1 texto, >= 3:1 controles).**
- **D4. Hierarquia de ação vs. destaque:**
  - Ação primária neutra (`ActionPrimary = #F3F2F0` com `ActionPrimaryText = #090909`).
  - Destaque de marca e foco: Dourado Havrus (`BrandGold = #C6A15B` / `BrandGoldStrong = #E8C881`).
  - Sinais com ícone/rótulo: Sucesso `#10B981`, Erro `#E58C98`, Aviso `#E2AE62`, Info `#3B82F6`.
- **D5. Quatro estados obrigatórios:** Carregando, Erro (com retry), Vazio e Dados.
- **D6. Família única de ícones (Material Icons) e zero emojis estruturais.**
- **D7. Alvos de toque >= 44×44 dp (`48×48 dp` recomendado) e respeito a acessibilidade.**
- **D8. Paridade 1:1 com o `HiveMind` IDE (`ide/frontend/src/styles.css`).**
