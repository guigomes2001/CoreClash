package com.example.coreclash.core.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.example.coreclash.core.runtime.CoreServices;

/**
 * Tela base de partida LibGDX (placeholder para migração gradual).
 */
public class MatchScreen extends ScreenAdapter {

    private final CoreServices services;

    public MatchScreen(CoreServices services) {
        this.services = services;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.1f, 0.18f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Próximos passos da migração:
        // 1) desenhar board + HUD
        // 2) animar skills/vitória via timeline/easing
        // 3) sincronizar input com MatchStatePort
        if (services.matchStatePort().isMatchOver()) {
            // placeholder de hook para result layer
        }
    }
}
