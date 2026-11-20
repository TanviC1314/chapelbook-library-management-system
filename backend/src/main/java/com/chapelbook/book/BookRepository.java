package com.chapelbook.book;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    boolean existsByCategoryId(Long categoryId);
}
