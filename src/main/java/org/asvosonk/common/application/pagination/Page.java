package org.asvosonk.common.application.pagination;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable paginated result wrapper.
 *
 * @param <T> the type of elements in this page
 */
public class Page<T> {

    private final List<T> content;
    private final int pageNumber;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;

    public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
        this.content = Collections.unmodifiableList(content);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = pageSize > 0
            ? (int) Math.ceil((double) totalElements / pageSize)
            : 0;
    }

    public List<T> getContent()          { return content; }
    public int getPageNumber()           { return pageNumber; }
    public int getPageSize()             { return pageSize; }
    public long getTotalElements()       { return totalElements; }
    public int getTotalPages()           { return totalPages; }
    public boolean hasNext()             { return pageNumber < totalPages - 1; }
    public boolean hasPrevious()         { return pageNumber > 0; }
    public boolean isEmpty()             { return content.isEmpty(); }

    public <R> Page<R> map(Function<? super T, ? extends R> converter) {
        List<R> converted = content.stream()
            .map(converter)
            .collect(Collectors.toList());
        return new Page<>(converted, pageNumber, pageSize, totalElements);
    }

    public static <T> Page<T> empty() {
        return new Page<>(Collections.emptyList(), 0, 0, 0);
    }
}
