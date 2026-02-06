package com.example.coreclash.core;

import com.badlogic.gdx.Game;
import com.example.coreclash.core.runtime.CoreServices;
import com.example.coreclash.core.screens.MatchScreen;

/**
 * Entry point para runtime LibGDX.
 *
 * Migração incremental: este módulo não substitui o app Android atual de uma vez,
 * ele prepara uma base limpa para evoluir animações/render com LibGDX.
 */
public class CoreClashGame extends Game {

    private final CoreServices services;

    public CoreClashGame(CoreServices services) {
        this.services = services;
    }

    @Override
    public void create() {
        setScreen(new MatchScreen(services));
    }
}
