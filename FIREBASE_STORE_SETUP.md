# Firebase + Loja (Microtransações) no Core Clash

## O que foi integrado
- `FirebaseAuth` (login anônimo para identificar jogador multiplayer).
- `Cloud Firestore` (persistência de perfil, moedas, progressão e cosméticos desbloqueados).
- `Google Play Billing` (produto in-app `coins_pack_small`, consumível — a compra é consumida via `consumeAsync`, permitindo recompra; compras pendentes são restauradas na inicialização).

## Economia do jogo
- Moedas por partida: vitória 25 + bônus de velocidade (até +9) + bônus de sequência (até +25); empate 8; derrota 3 (consolação). Partidas ranqueadas pagam +50%.
- Bônus diário com sequência: dia 1 = 40 moedas, escalando até 150 no dia 7+ (sequência quebra se pular um dia).
- Pontos ranqueados: vitória ganha, derrota perde 6 (nunca abaixo de 0). Rank exibido na home.
- Em builds **debug**, o botão de compra de moedas concede 500 moedas grátis como fallback de desenvolvimento quando o Billing não está disponível; em release mostra "loja indisponível".

## Checklist para ativar em produção
1. Adicionar `google-services.json` em `app/`.
2. Coleção `profiles` no Firestore (criada automaticamente no primeiro save).
3. Publicar no Play Console um produto in-app **consumível** com id `coins_pack_small`.
4. Testar o fluxo de compra com uma conta de teste de licença do Play Console (o fallback dev só existe em builds debug).

## Estrutura de perfil persistida
Documento `profiles/{uid}`:
- `displayName`
- `coins`
- `ownedThemes`
- `ownedSymbolStyles`
- `equippedTheme`
- `equippedSymbolStyle`
- `totalWins`
- `winStreak`
- `bestWinStreak`
- `rankedPoints`
- `lastDailyBonusEpochDay`
- `dailyBonusStreak`

O mesmo conjunto de campos é espelhado localmente em `SharedPreferences` (`LocalProfileRepository`) para modo offline.
