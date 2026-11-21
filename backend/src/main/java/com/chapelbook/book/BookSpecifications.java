package com.chapelbook.book;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the catalogue search. Each one is a filter that is only added when the caller asks for it. */
final class BookSpecifications {

    private static final int MAX_TERMS = 5;

    private BookSpecifications() {
    }

    /**
     * Every word typed must appear in the title, author or ISBN (case-insensitive). "clean martin" finds
     * "Clean Code" by "Robert Martin". Words are matched literally: "%" and "_" typed by the user are not wildcards.
     */
    static Specification<Book> matchesText(String query) {
        if (query == null || query.isBlank()) {
            return Specification.where(null);
        }
        String[] terms = query.trim().toLowerCase().split("\\s+");
        return (root, q, cb) -> {
            List<Predicate> perTerm = new ArrayList<>();
            for (int i = 0; i < Math.min(terms.length, MAX_TERMS); i++) {
                String pattern = "%" + escapeLike(terms[i]) + "%";
                perTerm.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("author")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("isbn"), "")), pattern, '\\')));
            }
            return cb.and(perTerm.toArray(Predicate[]::new));
        };
    }

    static Specification<Book> inCategory(Long categoryId) {
        if (categoryId == null) {
            return Specification.where(null);
        }
        return (root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** Only titles that have at least one copy on the shelf right now. */
    static Specification<Book> hasAvailableCopy(boolean availableOnly) {
        if (!availableOnly) {
            return Specification.where(null);
        }
        return (root, q, cb) -> {
            Subquery<Long> copies = q.subquery(Long.class);
            Root<BookCopy> copy = copies.from(BookCopy.class);
            copies.select(copy.get("id")).where(
                    cb.equal(copy.get("book"), root),
                    cb.equal(copy.get("status"), CopyStatus.AVAILABLE));
            return cb.exists(copies);
        };
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
