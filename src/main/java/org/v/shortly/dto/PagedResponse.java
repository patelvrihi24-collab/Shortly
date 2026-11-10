package org.v.shortly.dto;

import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
// Our own wrapper (generic <T>) instead of returning Spring's Page directly. That keeps the JSON shape
// stable even if Spring changes Page's internals.