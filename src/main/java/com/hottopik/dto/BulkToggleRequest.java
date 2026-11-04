package com.hottopik.dto;

public class BulkToggleRequest {
    private String type;     // "WORD", "TOPIC", "LEVEL", "TOPIC_VOCAB"
    private String target;   // ID, Topic Name, or Level (1-6)
    private Integer level;   // Optional level (1-6)
    private boolean active;  // true = ACTIVE, false = INACTIVE

    public BulkToggleRequest() {}

    public BulkToggleRequest(String type, String target, boolean active) {
        this.type = type;
        this.target = target;
        this.active = active;
    }

    public BulkToggleRequest(String type, String target, Integer level, boolean active) {
        this.type = type;
        this.target = target;
        this.level = level;
        this.active = active;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getTarget() {
        return target;
    }
    public void setTarget(String target) {
        this.target = target;
    }
    public Integer getLevel() {
        return level;
    }
    public void setLevel(Integer level) {
        this.level = level;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
