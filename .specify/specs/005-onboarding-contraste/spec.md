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

## Layout (revisão do portão, do pareamento e do login)

As três telas deixam de ser um cartão flutuante no meio do vazio. O conteúdo ocupa a tela e a ação principal fica ancorada embaixo, em pílula de largura total (`ActionPrimary` / `ActionPrimaryText`). A ação secundária é um link discreto ou uma pílula de `ObsidianSurface`.

- **Portão:** marca grande com halo dourado (`BrandGold`), headline, três linhas com ícone (equipe, QR, canal cifrado) e, no rodapé, Acesso remoto e Parear na rede local. Contas salvas entram na lista, não num cartão de botões.
- **Pareamento LAN:** visor de QR ilustrado como herói e os passos 1 Abra a IDE, 2 Config > Celular, 3 Aponte a câmera. A pílula de baixo é Escanear QR. Colar código abre o campo só quando pedido.
- **Relay:** o mesmo visor de QR, com os passos Abra a IDE, Acesso remoto e Aponte a câmera. O formulário (URL, e-mail, senha com ícone e mostrar/ocultar) fica recolhido em Entrar com e-mail. O botão Entrar permanece fixo embaixo. Uma conta salva abre direto nesse formulário. A pílula secundária usa borda `BorderControl` para não sumir no fundo.

## Diffs, Fluxo, IDEs e câmera

Essas quatro superfícies usam o mesmo esqueleto do portão: lavagem dourada no topo, headline, conteúdo em cartões e a ação principal numa pílula ancorada embaixo.

- **Diffs:** contagem grande, barra proporcional de linhas adicionadas e removidas, chips por extensão e, em cada arquivo, uma mini-barra. Vazio e erro têm ícone, não só uma faixa.
- **Fluxo:** headline do projeto, abas em pílula e cartão da task com avatar, status colorido e botão de resolver. Vazio e erro são ilustrados.
- **Suas IDEs:** cartão de máquina com ícone de computador ou notebook, ponto online e a rede que o app conhece (Relay e o host). Controlar é a pílula clara.
- **Câmera:** ilustração de QR, frase curta e pílula Permitir câmera. O texto da pílula clara é `ActionPrimaryText`.

