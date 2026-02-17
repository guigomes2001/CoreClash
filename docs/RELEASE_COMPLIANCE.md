# CoreClash - Release & Compliance

## 1) Definition of Done (Play Store Ready)
- [ ] Pre-launch report sem crash/ANR relevante.
- [ ] Data Safety preenchido e Política de Privacidade pública consistente.
- [ ] Fluxos críticos validados: login, partida, compra, restauração, offline/rede instável.
- [ ] Compatibilidade validada em telas pequenas, tablet, Android 14 e 15.
- [ ] Store listing completo e fiel às features reais.

## 2) Versionamento de release
Regra adotada:
- `versionName = MAJOR.MINOR.PATCH`
- `versionCode = MAJOR*10000 + MINOR*100 + PATCH`

Versão atual de produção:
- `versionName`: `1.0.1`
- `versionCode`: `10001`

Processo:
- Release normal: incrementa `MINOR` ou `PATCH`.
- Hotfix: incrementa apenas `PATCH`.
- Breaking change: incrementa `MAJOR`.

## 3) Data Safety (inventário interno)
| Categoria | Dado | Finalidade | Armazenamento | Retenção | Compartilha? | Segurança |
|---|---|---|---|---|---|---|
| Autenticação | UID / email (quando aplicável) | Login e perfil | Firebase Auth / Firestore | Enquanto conta existir | Processador (Google/Firebase) | TLS em trânsito + controles Firebase |
| Perfil | Nome de exibição, progresso, moedas | Estado do jogador e UX | Firestore / local profile | Enquanto conta existir / app instalado | Não (fora processadores) | Regras de acesso no backend |
| Compras | purchaseToken, sku, estado da compra | Entitlement e restauração | Google Play Billing + backend/perfil | Conforme política fiscal e suporte | Processador (Google Play) | APIs oficiais Billing + validação |
| Rede/diagnóstico | status básico de erros de conexão | Estabilidade | Local/log app | Curta | Não | Sem dado sensível em log |

## 4) Backup e segurança
Decisão atual:
- `allowBackup=false` para reduzir risco de exposição de sessão/token via backup.

Ações obrigatórias antes de publicar:
- Revisar se nenhum token/sessão é persistido em texto puro.
- Garantir que Data Safety e Política de Privacidade reflitam esta decisão.

## 5) Fluxos críticos a testar (happy + fail)
1. Login (anônimo/Google) com timeout e retry.
2. Partida offline e online (entrada, jogadas, fim de partida, rematch).
3. Compra de moedas (sucesso, cancelamento, falha de billing).
4. Restauração de compras com feedback ao usuário.
5. Offline/instável (mensagem clara + ação de tentativa novamente).

## 6) Matriz mínima de dispositivos
- Pequeno: 5.4" / 720p
- Médio: 6.1" FHD
- Tablet: 10"
- Android: 14 e 15

Critérios:
- Sem clipping de UI.
- Sem crash por permissão/rede.
- Match e compras funcionais.

## 7) Store listing checklist
- [ ] Ícone legível em 48x48.
- [ ] Screenshots reais: menu, match, loja, perfil, modos, resultado.
- [ ] Texto sem promessas não garantidas (ex.: "sem lag" absoluto).
- [ ] Política de privacidade publicada e URL válida.

## 8) Compliance antecipado
- Crianças: confirmar se app é direcionado a menores.
- UGC/chat: se existir, implementar moderação e report.
- Identificadores/analytics: declarar corretamente no Data Safety.

## 9) Itens finais antes de enviar para produção
- [ ] Gerar **AAB assinado** com keystore de produção e Play App Signing ativo.
- [ ] Preencher classificação indicativa (content rating questionnaire).
- [ ] Definir e testar canal de suporte (email/URL) no listing.
- [ ] Revisar permissões declaradas no manifest e remover as não usadas.
- [ ] Subir primeiro em **closed testing** e monitorar Android Vitals (crash, ANR, cold start).
- [ ] Validar faturamento real com contas de teste da Play Console.
- [ ] Confirmar que URL da política de privacidade está publicada e acessível.
