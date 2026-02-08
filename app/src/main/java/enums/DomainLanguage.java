package enums;

public enum DomainLanguage {
    ENGLISH("en", "English 🇺🇸"),
    PORTUGUESE("pt", "Português 🇧🇷");

    private final String tag;
    private final String displayName;

    DomainLanguage(String tag, String displayName) {
        this.tag = tag;
        this.displayName = displayName;
    }

    public String getTag() {
        return tag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static String[] getDisplayNames() {
        DomainLanguage[] allLanguages = values();
        String[] names = new String[allLanguages.length];

        for (int i = 0; i < allLanguages.length; i++) {
            names[i] = allLanguages[i].getDisplayName();
        }
        return names;
    }
}