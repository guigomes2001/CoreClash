# Core Clash Web (PWA)

Refatoração da versão web para ficar alinhada com o produto atual, incluindo:
- modo local completo
- modo online conectado a backend Java
- base PWA instalável em Android e iOS

## Rodando frontend

```bash
cd web-pwa
python -m http.server 4173
```

## API backend

O frontend consulta `http://localhost:8080/api` por padrão.
Você pode sobrescrever com `window.CORECLASH_API_BASE` antes de carregar `src/app.js`.

## Paridade total

Esta versão prepara a arquitetura para paridade com o app atual (Firebase/Auth/RTDB).
A paridade final depende de migrar todos os fluxos (store, perfil, ranking, social, bots e animações) em etapas.
