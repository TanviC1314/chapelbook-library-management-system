package com.chapelbook.book;

import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.PageResponse;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.reservation.ReservationRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    static final int MAX_PAGE_SIZE = 100;

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final CategoryRepository categories;
    private final ReservationRepository reservations;

    public BookService(BookRepository books, BookCopyRepository copies, CategoryRepository categories,
            ReservationRepository reservations) {
        this.books = books;
        this.copies = copies;
        this.categories = categories;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        Book book = find(id);
        return BookResponse.from(book, availabilityFor(List.of(id)).getOrDefault(id, BookAvailability.NONE));
    }

    /** The catalogue: filter by text, category and availability, one page at a time. */
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(String text, Long categoryId, boolean availableOnly, BookSort sort,
            int page, int size) {
        Specification<Book> filter = Specification.where(BookSpecifications.matchesText(text))
                .and(BookSpecifications.inCategory(categoryId))
                .and(BookSpecifications.hasAvailableCopy(availableOnly));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                sort.toSort());
        Page<Book> result = books.findAll(filter, pageable);
        Map<Long, BookAvailability> availability = availabilityFor(result.getContent().stream().map(Book::getId).toList());
        return PageResponse.of(result,
                book -> BookResponse.from(book, availability.getOrDefault(book.getId(), BookAvailability.NONE)));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        Category category = category(request.categoryId());
        String isbn = normaliseIsbn(request.isbn());
        if (isbn != null && books.existsByIsbn(isbn)) {
            throw new ConflictException("A book with this ISBN already exists.");
        }
        Book book = new Book(request.title(), request.author(), category);
        apply(book, request, isbn);
        Book saved = books.saveAndFlush(book);
        return BookResponse.from(saved, BookAvailability.NONE);
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = find(id);
        String isbn = normaliseIsbn(request.isbn());
        boolean isbnChanged = isbn != null && !isbn.equals(book.getIsbn());
        if (isbnChanged && books.existsByIsbn(isbn)) {
            throw new ConflictException("A book with this ISBN already exists.");
        }
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setCategory(category(request.categoryId()));
        apply(book, request, isbn);
        Book saved = books.saveAndFlush(book);
        return BookResponse.from(saved, availabilityFor(List.of(id)).getOrDefault(id, BookAvailability.NONE));
    }

    @Transactional
    public void delete(Long id) {
        Book book = find(id);
        if (copies.existsByBookId(id) || reservations.existsByBookId(id)) {
            throw new ConflictException(
                    "This book has copies or reservations, so its history must be kept. Withdraw its copies instead.");
        }
        books.delete(book);
    }

    @Transactional(readOnly = true)
    public Book find(Long id) {
        return books.findById(id).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
    }

    /** Live availability for a set of books using two grouped queries, whatever the number of books. */
    @Transactional(readOnly = true)
    public Map<Long, BookAvailability> availabilityFor(Collection<Long> bookIds) {
        Map<Long, int[]> counts = new HashMap<>(); // [total in circulation, available, pending]
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        for (CopyStatusCount row : copies.countByBookIds(bookIds)) {
            int[] c = counts.computeIfAbsent(row.getBookId(), k -> new int[3]);
            if (row.getStatus() == CopyStatus.AVAILABLE) {
                c[1] += (int) row.getTotal();
            }
            if (row.getStatus() == CopyStatus.AVAILABLE || row.getStatus() == CopyStatus.ON_LOAN
                    || row.getStatus() == CopyStatus.HELD) {
                c[0] += (int) row.getTotal();
            }
        }
        for (ReservationRepository.PendingCount row : reservations.countPendingByBookIds(bookIds)) {
            counts.computeIfAbsent(row.getBookId(), k -> new int[3])[2] = (int) row.getTotal();
        }
        Map<Long, BookAvailability> result = new HashMap<>();
        counts.forEach((id, c) -> result.put(id, new BookAvailability(c[0], c[1], c[2])));
        return result;
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new BusinessRuleException("Category does not exist."));
    }

    private static void apply(Book book, BookRequest request, String isbn) {
        book.setIsbn(isbn);
        book.setPublisher(blankToNull(request.publisher()));
        book.setPublicationYear(request.publicationYear());
        book.setDescription(blankToNull(request.description()));
    }

    /** Strips hyphens and spaces so "978-0-13-235088-4" and "9780132350884" are the same ISBN. */
    static String normaliseIsbn(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String isbn = raw.replaceAll("[\\s-]", "").toUpperCase();
        if (!isbn.matches("^(\\d{9}[\\dX]|\\d{13})$")) {
            throw new BusinessRuleException("An ISBN must have 10 or 13 digits.");
        }
        return isbn;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
