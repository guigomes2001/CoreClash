# Firebase + Loja (Microtransações) no Core Clash

## O que foi integrado
- `FirebaseAuth` (login anônimo para identificar jogador multiplayer).
- `Cloud Firestore` (persistência de perfil, moedas e cosméticos desbloqueados).
- `Google Play Billing` (produto in-app `coins_pack_small`).

## Checklist para ativar em produção
1. Adicionar `google-services.json` em `app/`.
2. Criar coleção `players` no Firestore (ou deixar criar automaticamente).
3. Publicar no Play Console um produto in-app com id `coins_pack_small`.
4. Trocar o fallback dev da loja por fluxo de compra real apenas.

## Estrutura de perfil persistida
Documento `players/{uid}`:
- `displayName`
- `coins`
- `ownedThemes`
- `ownedSymbolStyles`
- `equippedTheme`
- `equippedSymbolStyle`
