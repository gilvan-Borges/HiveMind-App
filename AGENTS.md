# Diretrizes para Agentes & Contribuidores — HiveMind-App

## Política de Repositório Público (OBRIGATÓRIA)

Este repositório (`HiveMind-App`) e o repositório principal (`gilvan-Borges/HiveMind`) são **repositórios públicos no GitHub**. Toda alteração de código, teste, fixture, comentário, spec ou commit **DEVE** obedecer às regras abaixo:

1. **Zero segredos ou arquivos sensíveis:** nunca commitar chaves de API, tokens, senhas, `.env`, keystores (`.jks`, `.keystore`), `.pem`/`.key`, `local.properties` ou arquivos de credenciais.
2. **Zero binários compilados no Git:** nunca versionar `.apk`, `.aab` ou builds gerados — distribua exclusivamente via **GitHub Releases**.
3. **Zero dados reais de terceiros, empresas ou clientes:** proibido incluir nomes de empresas/clientes privados, domínios corporativos reais, chaves reais de Jira/PRs, caminhos locais privados, usernames de terceiros ou e-mails pessoais/corporativos reais.
4. **Única identificação pessoal permitida:** dados do autor/mantenedor (**Gilvan Borges** / `gilvan-Borges`) em metadados do projeto, autoria de commits e licença.
5. **Dados sintéticos:** em exemplos e testes use exclusivamente dados fictícios (`acme`, `org-alpha`, `example.com`, `PROJ-123`, `@reviewer`).

## Design System & SDD
- Siga obrigatoriamente `.specify/memory/constitution.md` (Artigos `D1–D9`), mantendo paridade 1:1 com a paleta **Havrus Obsidian & Brand Gold**.
