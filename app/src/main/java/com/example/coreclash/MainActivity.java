package com.example.coreclash;

import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coreclash.billing.BillingManager;
import com.example.coreclash.data.FirebaseProfileRepository;
import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.model.PlayerProfile;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.coreclash.view.VictoryLineView;

import java.util.List;
import java.util.Random;

import game.GameState;
import manager.BoardManager;
import manager.GameManager;

public class MainActivity extends AppCompatActivity {

    private enum SelectedMode { CASUAL, RANKED }

    private enum BotDifficulty {
        INICIANTE("Iniciante"),
        MODERADA("Moderada"),
        MESTRE("Mestre do Jogo");

        final String label;

        BotDifficulty(String label) {
            this.label = label;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private GameManager gameManager;
    private GameState state;
    private BoardManager board;

    private TextView txtStatus;
    private TextView txtWinnerTitle;
    private TextView txtStatsMoves;
    private TextView txtStatsGhosts;
    private TextView txtVersusX;
    private TextView txtVersusO;
    private TextView txtVersusCenter;
    private VictoryLineView victoryLineView;

    private FrameLayout btnTriangle;
    private FrameLayout btnSquare;
    private FrameLayout homeOverlay;
    private FrameLayout versusOverlay;
    private FrameLayout victoryOverlay;
    private View victoryCard;
    private VictoryLineView victoryLineView;

    private FrameLayout storeOverlay;
    private FrameLayout storeTransitionOverlay;
    private View storeScreen;
    private TextView txtStoreCoinsFull;
    private TextView txtCurtainTop;
    private TextView txtCurtainMiddle;
    private TextView txtCurtainBottom;
    private Button btnStoreClose;
    private Button btnThemeRoyal;
    private Button btnThemeVoid;
    private Button btnStyleRune;
    private Button btnStyleFuture;
    private Button btnBuyCoins;

    private FrameLayout modeOverlay;
    private View modeCard;
    private Button btnModeCasual;
    private Button btnModeRanked;
    private Button btnModeCancel;
    private Button btnModeConfirm;

    private Button btnPlay;
    private Button btnOnline;
    private Button btnStore;
    private Button btnSettings;
    private Button btnRestart;
    private Button btnExit;
    private Button btnNewMatch;

    private boolean matchStarted = false;
    private boolean versusBot = false;
    private SelectedMode selectedMode = SelectedMode.CASUAL;
    private BotDifficulty currentBotDifficulty = BotDifficulty.INICIANTE;
    private String opponentName = "Aguardando";

    private ProfileRepository profileRepository;
    private LocalProfileRepository localProfileRepository;
    private BillingManager billingManager;
    private PlayerProfile currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemBars();
        setContentView(R.layout.activity_main);
        initUI();

        state = new GameState();
        board = new BoardManager();
        gameManager = new GameManager(board, state);

        GridLayout gridBoard = findViewById(R.id.gridBoard);
        board.createBoard(this, gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) {
                return;
            }

            if (versusBot && !state.isXTurn()) {
                return;
            }

            playTurn(row, col);
        });

        setupSkills();
        setupHomeFlow();
        initPlayerServices();
        setupMetaControls();

        showHomeScreen();
        updateModeButtonStyles();
        updateHeaderStatus();
        updateSkillVisuals();
    }

    private void initUI() {
        txtStatus = findViewById(R.id.txtStatus);
        txtWinnerTitle = findViewById(R.id.txtWinnerTitle);
        txtStatsMoves = findViewById(R.id.txtStatsMoves);
        txtStatsGhosts = findViewById(R.id.txtStatsGhosts);
        txtVersusX = findViewById(R.id.txtVersusX);
        txtVersusO = findViewById(R.id.txtVersusO);
        txtVersusCenter = findViewById(R.id.txtVersusCenter);
        victoryLineView = findViewById(R.id.victoryLineView);

        btnTriangle = findViewById(R.id.containerTriangle);
        btnSquare = findViewById(R.id.containerSquare);
        homeOverlay = findViewById(R.id.homeOverlay);
        versusOverlay = findViewById(R.id.versusOverlay);
        victoryOverlay = findViewById(R.id.victoryOverlay);
        victoryCard = findViewById(R.id.victoryCard);
        victoryLineView = findViewById(R.id.victoryLineView);

        storeOverlay = findViewById(R.id.storeOverlay);
        storeTransitionOverlay = findViewById(R.id.storeTransitionOverlay);
        storeScreen = findViewById(R.id.storeScreen);
        txtStoreCoinsFull = findViewById(R.id.txtStoreCoinsFull);
        txtCurtainTop = findViewById(R.id.txtCurtainTop);
        txtCurtainMiddle = findViewById(R.id.txtCurtainMiddle);
        txtCurtainBottom = findViewById(R.id.txtCurtainBottom);
        btnStoreClose = findViewById(R.id.btnStoreClose);
        btnThemeRoyal = findViewById(R.id.btnThemeRoyal);
        btnThemeVoid = findViewById(R.id.btnThemeVoid);
        btnStyleRune = findViewById(R.id.btnStyleRune);
        btnStyleFuture = findViewById(R.id.btnStyleFuture);
        btnBuyCoins = findViewById(R.id.btnBuyCoins);

        modeOverlay = findViewById(R.id.modeOverlay);
        modeCard = findViewById(R.id.modeCard);
        btnModeCasual = findViewById(R.id.btnModeCasual);
        btnModeRanked = findViewById(R.id.btnModeRanked);
        btnModeCancel = findViewById(R.id.btnModeCancel);
        btnModeConfirm = findViewById(R.id.btnModeConfirm);

        btnPlay = findViewById(R.id.btnPlay);
        btnOnline = findViewById(R.id.btnOnline);
        btnStore = findViewById(R.id.btnStore);
        btnSettings = findViewById(R.id.btnSettings);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
        btnNewMatch = findViewById(R.id.btnNewMatch);
    }

    private void setupHomeFlow() {
        btnPlay.setOnClickListener(v -> openModeModal());

        btnOnline.setOnClickListener(v -> {
            Toast.makeText(this, "Buscando partida online...", Toast.LENGTH_SHORT).show();
            startMatchmaking(true);
        });

        btnStore.setOnClickListener(v -> openStoreScreen());
        btnSettings.setOnClickListener(v -> Toast.makeText(this, "Configurações em desenvolvimento ⚙", Toast.LENGTH_SHORT).show());

        btnModeCasual.setOnClickListener(v -> {
            selectedMode = SelectedMode.CASUAL;
            updateModeButtonStyles();
        });

        btnModeRanked.setOnClickListener(v -> {
            selectedMode = SelectedMode.RANKED;
            updateModeButtonStyles();
        });

        btnModeCancel.setOnClickListener(v -> closeModeModal());
        btnModeConfirm.setOnClickListener(v -> {
            closeModeModal();
            startMatchmaking(false);
        });

        modeOverlay.setOnClickListener(v -> closeModeModal());
        modeCard.setOnClickListener(v -> {
            // consume click
        });
    }

    private void setupMetaControls() {
        btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            victoryLineView.clear();
            updateSkillVisuals();
            startMatchIntro();
        });

        btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            victoryLineView.clear();
            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });

        btnNewMatch.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            victoryLineView.clear();
            startMatchmaking(true);
        });
    }

    private void setupSkills() {
        btnTriangle.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver()) {
                shakeButton(v);
                return;
            }

            if (!state.canUseTriangle()) {
                shakeButton(v);
                return;
            }

            gameManager.useTriangle();
            spinAnimation(v);
            updateHeaderStatus();
            updateSkillVisuals();
        });

        btnSquare.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver()) {
                shakeButton(v);
                return;
            }

            if (!state.canUseSquare()) {
                shakeButton(v);
                return;
            }

            gameManager.useSquare();
            pulseAnimation(v);
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void openModeModal() {
        updateModeButtonStyles();
        modeOverlay.setVisibility(View.VISIBLE);
        modeOverlay.setAlpha(0f);
        modeCard.setScaleX(0.9f);
        modeCard.setScaleY(0.9f);

        modeOverlay.animate().alpha(1f).setDuration(180).start();
        modeCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    private void closeModeModal() {
        modeOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> modeOverlay.setVisibility(View.GONE))
                .start();
    }

    private void updateModeButtonStyles() {
        boolean casual = selectedMode == SelectedMode.CASUAL;
        int selectedBg = 0xFF22D3EE;
        int selectedText = 0xFF082F49;
        int defaultBg = 0xFF312E81;
        int defaultText = 0xFFE0E7FF;

        btnModeCasual.setBackgroundTintList(ColorStateList.valueOf(casual ? selectedBg : defaultBg));
        btnModeCasual.setTextColor(casual ? selectedText : defaultText);

        btnModeRanked.setBackgroundTintList(ColorStateList.valueOf(casual ? defaultBg : selectedBg));
        btnModeRanked.setTextColor(casual ? defaultText : selectedText);
    }

    private void startMatchmaking(boolean fromOnlineButton) {
        boolean foundPlayer = fromOnlineButton && random.nextFloat() < 0.45f;
        versusBot = !foundPlayer;

        if (versusBot) {
            opponentName = randomBotName();
            currentBotDifficulty = randomDifficulty();
        } else {
            opponentName = "RivalOnline" + (100 + random.nextInt(900));
            currentBotDifficulty = BotDifficulty.MODERADA;
            Toast.makeText(this, "Partida online encontrada contra " + opponentName, Toast.LENGTH_SHORT).show();
        }

        state.setGameMode(selectedMode == SelectedMode.RANKED ? GameState.GameMode.RANKED : GameState.GameMode.CASUAL);
        gameManager.resetGame();
        victoryLineView.clear();
        updateHeaderStatus();
        updateSkillVisuals();
        startMatchIntro();
    }

    private void showHomeScreen() {
        matchStarted = false;
        homeOverlay.setVisibility(View.VISIBLE);
        homeOverlay.setAlpha(1f);
        versusOverlay.setVisibility(View.GONE);
    }

    private void startMatchIntro() {
        homeOverlay.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction(() -> {
                    homeOverlay.setVisibility(View.GONE);
                    showVersusOverlay();
                })
                .start();
    }

    private void showVersusOverlay() {
        versusOverlay.setVisibility(View.VISIBLE);
        versusOverlay.setAlpha(0f);

        txtVersusX.setText("Você");
        txtVersusCenter.setText(selectedMode == SelectedMode.RANKED ? "RANK" : "CASUAL");
        txtVersusO.setText(opponentName);

        txtVersusX.setTranslationX(-220f);
        txtVersusO.setTranslationX(220f);
        txtVersusCenter.setScaleX(0.7f);
        txtVersusCenter.setScaleY(0.7f);

        versusOverlay.animate().alpha(1f).setDuration(160).start();
        txtVersusX.animate().translationX(0f).setDuration(480).setInterpolator(new OvershootInterpolator(1.1f)).start();
        txtVersusO.animate().translationX(0f).setDuration(480).setInterpolator(new OvershootInterpolator(1.1f)).start();
        txtVersusCenter.animate().scaleX(1.1f).scaleY(1.1f).setDuration(240)
                .withEndAction(() -> txtVersusCenter.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                .start();

        handler.postDelayed(() -> {
            versusOverlay.animate()
                    .alpha(0f)
                    .setDuration(240)
                    .withEndAction(() -> {
                        versusOverlay.setVisibility(View.GONE);
                        matchStarted = true;
                        updateHeaderStatus();
                        updateSkillVisuals();
                        maybeRunBotTurn();
                    })
                    .start();
        }, 1300);
    }

    private void playTurn(int row, int col) {
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            drawVictoryLine();
            handler.postDelayed(() -> showVictoryScreen(symbol), 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            drawDrawLine();
            handler.postDelayed(this::showDrawScreen, 420);
            return;
        }

        maybeRunBotTurn();
    }

    private void maybeRunBotTurn() {
        if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
            return;
        }

        handler.postDelayed(() -> {
            if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
                return;
            }

            if (currentBotDifficulty == BotDifficulty.MESTRE) {
                if (state.canUseTriangle() && random.nextFloat() < 0.35f) {
                    gameManager.useTriangle();
                }
                if (state.canUseSquare() && random.nextFloat() < 0.25f) {
                    gameManager.useSquare();
                }
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) {
                playTurn(move[0], move[1]);
            }
        }, 550);
    }

    private int[] chooseBotMove(BotDifficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (moves.isEmpty()) return null;

        if (difficulty == BotDifficulty.INICIANTE) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor("O");
        if (win != null) return win;

        int[] block = gameManager.findWinningMoveFor("X");
        if (block != null) return block;

        if (difficulty == BotDifficulty.MODERADA) {
            int[] center = gameManager.getCenterIfAvailable();
            return center != null ? center : moves.get(random.nextInt(moves.size()));
        }

        int[] best = gameManager.findBestMoveForO();
        return best != null ? best : moves.get(random.nextInt(moves.size()));
    }

    private void drawVictoryLine() {
        GameManager.WinInfo win = gameManager.getLastWin();
        if (win == null) return;

        PointF start = board.getCellCenterOnScreen(win.r1(), win.c1());
        PointF end = board.getCellCenterOnScreen(win.r3(), win.c3());

        int[] lineLoc = new int[2];
        victoryLineView.getLocationOnScreen(lineLoc);

        victoryLineView.setData(
                start.x - lineLoc[0],
                start.y - lineLoc[1],
                end.x - lineLoc[0],
                end.y - lineLoc[1]
        );
    }


    private void drawDrawLine() {
        PointF topLeft = board.getCellCenterOnScreen(0, 0);
        PointF topRight = board.getCellCenterOnScreen(0, 2);
        PointF bottomLeft = board.getCellCenterOnScreen(2, 0);
        PointF bottomRight = board.getCellCenterOnScreen(2, 2);

        int[] lineLoc = new int[2];
        victoryLineView.getLocationOnScreen(lineLoc);

        victoryLineView.setDrawData(
                topLeft.x - lineLoc[0],
                topLeft.y - lineLoc[1],
                bottomRight.x - lineLoc[0],
                bottomRight.y - lineLoc[1],
                topRight.x - lineLoc[0],
                topRight.y - lineLoc[1],
                bottomLeft.x - lineLoc[0],
                bottomLeft.y - lineLoc[1]
        );
    }

    private void showDrawScreen() {
        txtWinnerTitle.setText("EMPATE");
        txtStatsMoves.setText("⚡ MOV: " + gameManager.getFinalMoves() + " | =");
        txtStatsGhosts.setText("👻 GHO: " + String.format("%02d", gameManager.getFinalGhosts()) + " | 🔥 STK: " + gameManager.getWinStreak());

        victoryOverlay.setVisibility(View.VISIBLE);
        victoryOverlay.setAlpha(0f);
        victoryCard.setTranslationY(300f);

        victoryOverlay.animate().alpha(1f).setDuration(280).start();
        victoryCard.animate().translationY(0f).setDuration(520).setInterpolator(new OvershootInterpolator(1f)).start();
    }

    private void showVictoryScreen(String winner) {
        txtWinnerTitle.setText("" + winner + " WIN");
        txtStatsMoves.setText("⚡ MOV: " + gameManager.getFinalMoves() + " | 🏆 W: " + gameManager.getTotalWins());
        txtStatsGhosts.setText("👻 GHO: " + String.format("%02d", gameManager.getFinalGhosts()) + " | 🔥 STK: " + gameManager.getWinStreak());

        victoryOverlay.setVisibility(View.VISIBLE);
        victoryOverlay.setAlpha(0f);
        victoryCard.setTranslationY(300f);

        victoryOverlay.animate().alpha(1f).setDuration(280).start();
        victoryCard.animate().translationY(0f).setDuration(520).setInterpolator(new OvershootInterpolator(1f)).start();
    }

    private void hideVictoryScreen() {
        victoryOverlay.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> victoryOverlay.setVisibility(View.GONE))
                .start();
    }

    private void updateHeaderStatus() {
        String modeLabel = selectedMode == SelectedMode.RANKED ? "RANK" : "CASUAL";
        String rivalLabel = opponentName;
        String coins = currentProfile == null ? "--" : String.valueOf(currentProfile.coins);
        txtStatus.setText("CC • " + modeLabel + " • " + rivalLabel + " • ⬢" + coins);
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() && matchStarted ? 1f : 0.25f;
        float sqAlpha = state.canUseSquare() && matchStarted ? 1f : 0.25f;

        btnTriangle.animate().alpha(triAlpha).setDuration(220).start();
        btnSquare.animate().alpha(sqAlpha).setDuration(220).start();

        btnTriangle.setElevation(state.canUseTriangle() && matchStarted ? 20f : 0f);
        btnSquare.setElevation(state.canUseSquare() && matchStarted ? 20f : 0f);
    }

    private String randomBotName() {
        String[] names = {
                "GuaxinimDaNoite", "NinjaDoVazio", "CorujaSuprema", "TigreNebuloso",
                "FalcaoTatico", "LinceArcano", "DracoDeAço", "RaptorDigital"
        };
        return names[random.nextInt(names.length)];
    }

    private BotDifficulty randomDifficulty() {
        BotDifficulty[] levels = BotDifficulty.values();
        return levels[random.nextInt(levels.length)];
    }

    private void initPlayerServices() {
        localProfileRepository = new LocalProfileRepository(this);
        billingManager = new BillingManager();
        billingManager.start(this, amount -> {
            if (currentProfile == null) return;
            currentProfile.coins += amount;
            persistProfile();
            updateHeaderStatus();
            refreshStoreHeader();
            Toast.makeText(this, "+" + amount + " Core Coins", Toast.LENGTH_SHORT).show();
        });

        try {
            profileRepository = new FirebaseProfileRepository();
        } catch (Exception ignored) {
            profileRepository = localProfileRepository;
        }

        profileRepository.loadOrCreateProfile(new ProfileRepository.Callback() {
            @Override
            public void onSuccess(PlayerProfile profile) {
                currentProfile = profile;
                localProfileRepository.saveProfile(profile);
                applyEquippedCosmetics();
                updateHeaderStatus();
            }

            @Override
            public void onError(String error) {
                localProfileRepository.loadOrCreateProfile(new ProfileRepository.Callback() {
                    @Override
                    public void onSuccess(PlayerProfile profile) {
                        currentProfile = profile;
                        applyEquippedCosmetics();
                        updateHeaderStatus();
                        Toast.makeText(MainActivity.this, "Modo offline ativo para perfil", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String localError) {
                        Toast.makeText(MainActivity.this, "Falha ao carregar perfil", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void applyEquippedCosmetics() {
        if (currentProfile == null) return;
        board.setBoardTheme(currentProfile.equippedTheme);
        board.setSymbolStyle(currentProfile.equippedSymbolStyle);
        board.resetBoard();
    }

    private void openStoreScreen() {
        if (currentProfile == null) {
            Toast.makeText(this, "Perfil ainda carregando...", Toast.LENGTH_SHORT).show();
            return;
        }

        setupStoreActions();
        txtStoreCoinsFull.setText("Core Coins: " + currentProfile.coins);

        playStoreTransition(() -> {
            storeOverlay.setVisibility(View.VISIBLE);
            storeOverlay.setAlpha(0f);
            storeScreen.setTranslationY(40f);
            storeOverlay.animate().alpha(1f).setDuration(200).start();
            storeScreen.animate().translationY(0f).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        });
    }

    private void closeStoreScreen() {
        storeScreen.animate().translationY(30f).setDuration(160).start();
        storeOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> storeOverlay.setVisibility(View.GONE))
                .start();
    }

    private void setupStoreActions() {
        btnStoreClose.setOnClickListener(v -> closeStoreScreen());

        btnThemeRoyal.setOnClickListener(v ->
                buyOrEquipTheme("ROYAL", 180, this::refreshStoreHeader));

        btnThemeVoid.setOnClickListener(v ->
                buyOrEquipTheme("VOID", 220, this::refreshStoreHeader));

        btnStyleRune.setOnClickListener(v ->
                buyOrEquipStyle("RUNE", 140, this::refreshStoreHeader));

        btnStyleFuture.setOnClickListener(v ->
                buyOrEquipStyle("FUTURE", 160, this::refreshStoreHeader));

        btnBuyCoins.setOnClickListener(v -> {
            boolean launched = billingManager.launchCoinsPackPurchase(this);
            if (!launched) {
                currentProfile.coins += 500;
                persistProfile();
                refreshStoreHeader();
                updateHeaderStatus();
                Toast.makeText(this, "Pack dev aplicado (+500)", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshStoreHeader() {
        if (currentProfile == null) return;
        txtStoreCoinsFull.setText("Core Coins: " + currentProfile.coins);
    }

    private void playStoreTransition(Runnable onEnd) {
        storeTransitionOverlay.setVisibility(View.VISIBLE);
        storeTransitionOverlay.setAlpha(0f);

        txtCurtainTop.setTranslationX(-240f);
        txtCurtainMiddle.setTranslationX(240f);
        txtCurtainBottom.setTranslationX(-240f);

        storeTransitionOverlay.animate().alpha(1f).setDuration(120).start();
        txtCurtainTop.animate().translationX(0f).setDuration(240).start();
        txtCurtainMiddle.animate().translationX(0f).setDuration(280).start();
        txtCurtainBottom.animate().translationX(0f).setDuration(320).start();

        handler.postDelayed(() -> {
            if (onEnd != null) onEnd.run();
            storeTransitionOverlay.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction(() -> storeTransitionOverlay.setVisibility(View.GONE))
                    .start();
        }, 360);
    }

    private void buyOrEquipTheme(String themeId, int price, Runnable refresh) {
        if (currentProfile.ownsTheme(themeId)) {
            currentProfile.equippedTheme = themeId;
            persistProfile();
            applyEquippedCosmetics();
            Toast.makeText(this, "Tema equipado", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentProfile.coins < price) {
            Toast.makeText(this, "Moedas insuficientes", Toast.LENGTH_SHORT).show();
            return;
        }

        currentProfile.coins -= price;
        currentProfile.ownedThemes.add(themeId);
        currentProfile.equippedTheme = themeId;
        persistProfile();
        applyEquippedCosmetics();
        refresh.run();
        updateHeaderStatus();
    }

    private void buyOrEquipStyle(String styleId, int price, Runnable refresh) {
        if (currentProfile.ownsSymbolStyle(styleId)) {
            currentProfile.equippedSymbolStyle = styleId;
            persistProfile();
            applyEquippedCosmetics();
            Toast.makeText(this, "Estilo equipado", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentProfile.coins < price) {
            Toast.makeText(this, "Moedas insuficientes", Toast.LENGTH_SHORT).show();
            return;
        }

        currentProfile.coins -= price;
        currentProfile.ownedSymbolStyles.add(styleId);
        currentProfile.equippedSymbolStyle = styleId;
        persistProfile();
        applyEquippedCosmetics();
        refresh.run();
        updateHeaderStatus();
    }

    private void persistProfile() {
        if (currentProfile == null) return;
        localProfileRepository.saveProfile(currentProfile);
        if (profileRepository != null) {
            profileRepository.saveProfile(currentProfile);
        }
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void pulseAnimation(View v) {
        v.animate().cancel();
        v.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(90)
                .withEndAction(() -> v.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .setDuration(130)
                        .withEndAction(() -> v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(140)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start())
                        .start())
                .start();
    }

    private void spinAnimation(View v) {
        v.animate().cancel();
        v.animate()
                .rotationBy(360f)
                .setDuration(520)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withStartAction(() -> v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160).start())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private void shakeButton(View v) {
        v.animate().cancel();
        v.animate().translationX(10f).setDuration(40).withEndAction(() ->
                v.animate().translationX(-8f).setDuration(40).withEndAction(() ->
                        v.animate().translationX(6f).setDuration(35).withEndAction(() ->
                                v.animate().translationX(0f).setDuration(35).start()).start()).start()).start();
    }
}
