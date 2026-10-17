package com.hottopik.dto;

import java.io.Serializable;

public class ReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String authorName;
    private int rating;
    private String comment;
    private boolean approved;
    private String createdAt;

    public ReviewDTO() {}

    public ReviewDTO(Long id, String authorName, int rating, String comment, boolean approved, String createdAt) {
        this.id = id;
        this.authorName = authorName;
        this.rating = rating;
        this.comment = comment;
        this.approved = approved;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
