package com.chapelbook.book;

/** One row of "how many copies of this book have this status", from a single grouped query. */
public interface CopyStatusCount {

    Long getBookId();

    CopyStatus getStatus();

    long getTotal();
}
