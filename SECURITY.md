# Política de Segurança (Security Policy) — HiveMind-App

A segurança do **HiveMind-App** (cliente Android nativo em Kotlin e Jetpack Compose) e da sua integração com o **HiveMind** (IDE Desktop, CLI e `hivemind-relay`) é tratada como prioridade máxima, especialmente nos subsistemas de criptografia ponta a ponta (`NaClBox` X25519 + XSalsa20-Poly1305), verificação TOFU de chaves (`KeyTrust`), armazenamento seguro no Android Keystore (`KeystoreLanVault`, `KeystoreAccountVault`), proteção contra tráfego em texto claro fora da LAN (`CleartextGuard`) e redação de logs (`Redact`).

## Versões Suportadas

As correções de segurança são aplicadas continuamente na branch `main` e empacotadas na versão mais recente disponível em [GitHub Releases](https://github.com/gilvan-Borges/HiveMind-App/releases).

| Versão | Suportada |
| :--- | :--- |
| Última release (`latest`) / `main` | ✅ Sim |
| Releases anteriores | ❌ Não (atualize para a versão mais recente) |

## Como Reportar uma Vulnerabilidade

**Por favor, NÃO abra uma Issue pública nem um Pull Request público para relatar vulnerabilidades de segurança.**

Utilize o canal oficial privado do GitHub:

1. Acesse a aba **[Security → Advisories](https://github.com/gilvan-Borges/HiveMind-App/security/advisories/new)** deste repositório e clique em **"Report a vulnerability"** (Private Vulnerability Reporting).
2. Inclua no relato:
   - Componente afetado (ex.: `core/relay`, `core/lan`, `core/store`, `core/outbox`, `features/pairing`);
   - Passo a passo mínimo para reprodução (PoC);
   - Impacto estimado (ex.: bypass de TOFU, exposição de token em log/cache, aceitação de HTTP fora de redes RFC1918/Tailscale).

## O que Esperar

- **Confirmação de recebimento:** em até **48 horas**.
- **Triagem e plano de correção:** em até **7 dias**, com classificação de severidade e criação de branch privada no GitHub Security Advisory quando aplicável.
- **Divulgação coordenada:** após a publicação da release corrigida, o advisory é publicado com os devidos créditos ao pesquisador (salvo pedido de anonimato).

## Escopo Principal de Segurança

- Pareamento LAN v2 (`hivemind://pair?v=2`) e transporte autenticado/cifrado com `NaClBox`;
- Túnel WebSocket E2EE com o `hivemind-relay` e rotação de chaves TOFU (`KeyTrust`);
- Armazenamento de chaves privadas e tokens de sessão no Android Keystore (`AES/GCM/NoPadding`);
- Guard de tráfego HTTP restrito a loopback, RFC1918 e CGNAT Tailscale (`CleartextGuard`);
- Redação automática de tokens, senhas e chaves privadas em logs (`Redact`).
