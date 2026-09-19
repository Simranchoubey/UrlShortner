package com.example.urlshortener.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A single redirect "click" for analytics.
 *
 * <p>Written asynchronously by the analytics consumer (Phase 8). The
 * {@code shortCode} is denormalized onto the event so analytics do not need to
 * join back to {@code urls} purely for reporting.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @Column(name = "short_code", nullable = false, length = 12)
    private String shortCode;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(length = 2048)
    private String referrer;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(length = 45)
    private String ip;

    @Column(length = 2)
    private String country;

    /** Assigns an event timestamp on first persist if one is not already set. */
    @PrePersist
    void onCreate() {
        if (eventTime == null) {
            eventTime = OffsetDateTime.now();
        }
    }

    // -- Getters / setters ---------------------------------------------------

    public Long getId() {
        return id;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(OffsetDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}