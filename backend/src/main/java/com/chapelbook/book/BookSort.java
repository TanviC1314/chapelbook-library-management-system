package com.chapelbook.book;

import org.springframework.data.domain.Sort;

/** The orderings the catalogue offers. A whitelist, so a client can never sort by an arbitrary column. */
public enum BookSort {
    TITLE(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"))),
    AUTHOR(Sort.by(Sort.Order.asc("author"), Sort.Order.asc("title"), Sort.Order.asc("id"))),
    YEAR(Sort.by(Sort.Order.desc("publicationYear"), Sort.Order.asc("title"), Sort.Order.asc("id"))),
    NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    BookSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
