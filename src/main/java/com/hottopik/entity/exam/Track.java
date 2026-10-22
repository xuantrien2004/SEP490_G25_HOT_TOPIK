package com.hottopik.entity.exam;

public enum Track {
    TOPIK_I("TOPIK I"),
    TOPIK_II("TOPIK II");

    private final String label;

    Track(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
