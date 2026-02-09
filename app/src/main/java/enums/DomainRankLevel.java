package enums;

public enum DomainRankLevel {

    ROOKIE("CORE ROOKIE", 0),
    RECRUIT("CORE RECRUIT", 10),
    VETERAN("CORE VETERAN", 40),
    ELITE("CORE ELITE", 80),
    MASTER("CORE MASTER", 120);

    private final String label;
    private final int minPoints;

    DomainRankLevel(String label, int minPoints) {
        this.label = label;
        this.minPoints = minPoints;
    }

    public String getLabel() {
        return label;
    }

    public static DomainRankLevel fromPoints(int points) {
        if (points >= MASTER.minPoints) {
            return MASTER;
        }
        if (points >= ELITE.minPoints) {
            return ELITE;
        }
        if (points >= VETERAN.minPoints) {
            return VETERAN;
        }
        if (points >= RECRUIT.minPoints) {
            return RECRUIT;
        }
        return ROOKIE;
    }
}
