package com.chapelbook.category;

import com.chapelbook.book.BookRepository;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final BookRepository books;

    public CategoryService(CategoryRepository categories, BookRepository books) {
        this.categories = categories;
        this.books = books;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name").ascending()).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists.");
        }
        return CategoryResponse.from(categories.saveAndFlush(new Category(name, request.description())));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        boolean renamed = !category.getName().equalsIgnoreCase(name);
        if (renamed && categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists.");
        }
        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categories.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (books.existsByCategoryId(id)) {
            throw new ConflictException("This category still has books. Move or remove them first.");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    public Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
    }
}
