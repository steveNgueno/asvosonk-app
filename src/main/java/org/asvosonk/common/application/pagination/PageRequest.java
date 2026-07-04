package org.asvosonk.common.application.pagination;

/**
 * Immutable pagination request parameters.
 */
public class PageRequest {

    private final int pageNumber;
    private final int pageSize;

    public PageRequest(int pageNumber, int pageSize) {
        if (pageNumber < 0) throw new IllegalArgumentException("Page number must be >= 0");
        if (pageSize < 1)  throw new IllegalArgumentException("Page size must be >= 1");
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public int getPageNumber() { return pageNumber; }
    public int getPageSize()   { return pageSize; }
    public int getOffset()     { return pageNumber * pageSize; }

    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize);
    }

    public static PageRequest first(int pageSize) {
        return new PageRequest(0, pageSize);
    }
}
