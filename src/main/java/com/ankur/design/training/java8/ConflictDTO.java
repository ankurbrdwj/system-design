package com.ankur.design.training.java8;

public class ConflictDTO {

    public enum ConflictType {
        BonusLevel("Bonus Level"),
        SideGamePaytable("Promo Game Paytable"),
        MetricCriteria("Eligibility Name"),
        SlotsContentPackage("Slot Content Package"),
        SlotsContentFile("Slot Content File"),
        SlotPatronTier("Patron Tier");
        private String displayName;

        ConflictType(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private ConflictType type;
    private String originalName;
    private String overrideName;
    private boolean useExisting;

    public ConflictDTO() {
        this.useExisting=false;
    }

    public ConflictType getType() {
        return type;
    }

    public void setType(ConflictType type) {
        this.type = type;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public boolean isUseExisting() {
        return useExisting;
    }

    public void setUseExisting(boolean useExisting) {
        this.useExisting = useExisting;
    }

    public String getOverrideName() {
        return overrideName;
    }

    public void setOverrideName(String overrideName) {
        this.overrideName = overrideName;
    }

    @Override
    public String toString() {
        return "ConflictDTO{" +
                "type=" + type +
                ", originalName='" + originalName + '\'' +
                ", overrideName='" + overrideName + '\'' +
                ", useExisting=" + useExisting +
                '}';
    }
}

