package net.ronm19.wolfism.entity;

/**
 * Universal command states issued by the Wolf Staff.
 */
public enum WolfStaffCommandMode {
    FOLLOW("follow"),
    SIT("sit"),
    GUARD("guard"),
    ATTACK("attack");

    private final String serializedName;

    WolfStaffCommandMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public WolfStaffCommandMode next() {
        return switch (this) {
            case FOLLOW -> SIT;
            case SIT -> GUARD;
            case GUARD -> ATTACK;
            case ATTACK -> FOLLOW;
        };
    }

    public static WolfStaffCommandMode byName(String name) {
        for (WolfStaffCommandMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return FOLLOW;
    }
}
