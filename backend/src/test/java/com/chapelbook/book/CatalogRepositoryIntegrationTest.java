package com.chapelbook.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class CatalogRepositoryIntegrationTest {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private BookRepository books;

    @Autowired
    private BookCopyRepository copies;

    private Category computing;
    private Book book;

    @BeforeEach
    void setUp() {
        computing = categories.saveAndFlush(new Category("Computing", "Software and hardware"));
        book = new Book("Clean Code", "Robert Martin", computing);
        book.setIsbn("9780132350884");
        book.setPublicationYear(2008);
        book = books.saveAndFlush(book);
    }

    @Test
    void savesABookWithItsCategory() {
        Book loaded = books.findById(book.getId()).orElseThrow();

        assertThat(loaded.getTitle()).isEqualTo("Clean Code");
        assertThat(loaded.getCategory().getName()).isEqualTo("Computing");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void findsByIsbnAndChecksCategoryUse() {
        assertThat(books.findByIsbn("9780132350884")).isPresent();
        assertThat(books.existsByIsbn("000")).isFalse();
        assertThat(books.existsByCategoryId(computing.getId())).isTrue();
    }

    @Test
    void categoryNameLookupIgnoresCase() {
        assertThat(categories.findByNameIgnoreCase("COMPUTING")).isPresent();
        assertThat(categories.existsByNameIgnoreCase("computing")).isTrue();
    }

    @Test
    void copiesStartAvailableAndCanBeCountedByStatus() {
        copies.saveAndFlush(new BookCopy(book, "BC-1", "A1"));
        BookCopy second = copies.saveAndFlush(new BookCopy(book, "BC-2", "A1"));
        second.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(second);

        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE)).isEqualTo(1);
        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.ON_LOAN)).isEqualTo(1);
        assertThat(copies.findByBookIdOrderByBarcode(book.getId())).extracting(BookCopy::getBarcode)
                .containsExactly("BC-1", "BC-2");
        assertThat(copies.findByBarcode("BC-2")).isPresent();
    }

    @Test
    void duplicateBarcodeIsRejected() {
        copies.saveAndFlush(new BookCopy(book, "BC-DUP", null));

        assertThatThrownBy(() -> copies.saveAndFlush(new BookCopy(book, "BC-DUP", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockedAvailableQueryReturnsOnlyAvailableCopiesInOrder() {
        BookCopy first = copies.saveAndFlush(new BookCopy(book, "BC-A", null));
        BookCopy lent = copies.saveAndFlush(new BookCopy(book, "BC-B", null));
        lent.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(lent);
        BookCopy third = copies.saveAndFlush(new BookCopy(book, "BC-C", null));

        var available = copies.findAvailableForUpdate(book.getId(), PageRequest.of(0, 5));

        assertThat(available).extracting(BookCopy::getId).containsExactly(first.getId(), third.getId());
        assertThat(copies.findByIdForUpdate(first.getId())).isPresent();
    }
}
