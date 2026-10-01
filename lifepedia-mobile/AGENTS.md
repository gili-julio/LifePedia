# AGENTS.md — lifepedia-mobile

Pasta reservada para o app mobile. **Não há código ainda** — não crie projeto aqui sem o usuário pedir.

Quando começar: leia [`../AGENTS.md`](../AGENTS.md) (regras gerais, **segurança** e a regra de **documentação
sempre atualizada**), escolha a stack com o usuário e atualize este arquivo e o `README.md` com comandos,
estrutura e convenções.

Autenticação no app: envie `X-Auth-Mode: token` no login/cadastro para receber o JWT no corpo e use
`Authorization: Bearer <token>` nas demais chamadas (ver `lifepedia-api/README.md`). Guarde o token só no
armazenamento seguro do sistema (Keychain / Android Keystore), nunca em armazenamento comum.
