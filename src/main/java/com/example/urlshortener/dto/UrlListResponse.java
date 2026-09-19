package com.example.urlshortener.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/urls} — a page of the authenticated
 * user's own URLs plus pagination metadata (newest-first).
 *
 * @param items        the URL rows on this page
 * @param page         the zero-based page index returned
 * @param size         the page size actually applied
 * @param totalElements the total number of the user's URLs across all pages
 * @param totalPages   the total number of pages
 * @param first        whether this is the first page
 * @param last         whether this is the last page
 */
public record UrlListResponse(
        List<UrlResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}