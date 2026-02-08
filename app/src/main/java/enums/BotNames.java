package enums;

public enum BotNames {

    GUAXINIM_DA_NOITE("GuaxinimDaNoite"),
    NINJA_DO_VAZIO("NinjaDoVazio"),
    CORUJA_SUPREMA("CorujaSuprema"),
    TIGRE_NEBULOSO("TigreNebuloso"),
    FALCAO_TATICO("FalcaoTatico"),
    LINCE_ARCANO("LinceArcano"),
    DRACO_DE_ACO("DracoDeAço"),
    RAPTOR_DIGITAL("RaptorDigital");

    private final String displayName;

    BotNames(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
