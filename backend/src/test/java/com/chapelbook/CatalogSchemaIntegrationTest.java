package com.chapelbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the users and catalogue tables reject invalid data at the database level, whatever the
 * application code does. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
@Transactional
class CatalogSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long insertUser(String email, String role, String studentNumber) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role, student_number)
                VALUES (?, 'hash', 'Test User', ?, ?) RETURNING id
                """, Long.class, email, role, studentNumber);
    }

    private long insertCategory(String name) {
        return jdbc.queryForObject("INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private long insertBook(String isbn, long categoryId) {
        return jdbc.queryForObject("""
                INSERT INTO books (isbn, title, author, category_id)
                VALUES (?, 'Clean Code', 'Robert Martin', ?) RETURNING id
                """, Long.class, isbn, categoryId);
    }

    private void insertCopy(long bookId, String barcode, String status) {
        jdbc.update("INSERT INTO book_copies (book_id, barcode, status) VALUES (?, ?, ?)", bookId, barcode, status);
    }

    @Test
    void acceptsValidUserCategoryBookAndCopy() {
        insertUser("ann@example.com", "STUDENT", "S100");
        long book = insertBook("9780132350884", insertCategory("Computing"));
        insertCopy(book, "BC-1", "AVAILABLE");

        Integer copies = jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE book_id = ?", Integer.class, book);
        assertThat(copies).isEqualTo(1);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        insertUser("Ann@Example.com", "STUDENT", null);

        assertThatThrownBy(() -> insertUser("ann@example.com", "LIBRARIAN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> insertUser("x@example.com", "SUPERUSER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void studentNumberIsUnique() {
        insertUser("a@example.com", "STUDENT", "S200");

        assertThatThrownBy(() -> insertUser("b@example.com", "STUDENT", "S200"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void librarianCannotHaveAStudentNumber() {
        assertThatThrownBy(() -> insertUser("lib@example.com", "LIBRARIAN", "S300"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void categoryNameIsUniqueIgnoringCase() {
        insertCategory("Science");

        assertThatThrownBy(() -> insertCategory("science"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void isbnIsUniqueButSeveralBooksMayHaveNone() {
        long category = insertCategory("History");
        insertBook(null, category);
        insertBook(null, category);
        insertBook("111", category);

        assertThatThrownBy(() -> insertBook("111", category))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bookMustReferenceAnExistingCategory() {
        assertThatThrownBy(() -> insertBook("222", 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankTitle() {
        long category = insertCategory("Art");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO books (title, author, category_id) VALUES ('   ', 'A', ?)", category))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPublicationYearOutOfRange() {
        long category = insertCategory("Art");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO books (title, author, publication_year, category_id) VALUES ('T', 'A', 12, ?)", category))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void copyBarcodeIsUnique() {
        long book = insertBook(null, insertCategory("Law"));
        insertCopy(book, "BC-DUP", "AVAILABLE");

        assertThatThrownBy(() -> insertCopy(book, "BC-DUP", "AVAILABLE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownCopyStatus() {
        long book = insertBook(null, insertCategory("Law"));

        assertThatThrownBy(() -> insertCopy(book, "BC-2", "BORROWED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cannotDeleteACategoryThatStillHasBooks() {
        long category = insertCategory("Maths");
        insertBook(null, category);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = ?", category))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void titleSearchUsesTheTrigramIndex() {
        long category = insertCategory("Computing");
        insertBook(null, category);
        // Force the planner to consider the index even on a tiny table.
        jdbc.execute("SET LOCAL enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT id FROM books WHERE lower(title) LIKE '%code%'", String.class));

        assertThat(plan).contains("ix_books_title_trgm");
    }
}
