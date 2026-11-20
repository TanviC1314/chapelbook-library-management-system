-- Users, categories, books and book copies.

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           TEXT         NOT NULL,
    password_hash   TEXT         NOT NULL,
    full_name       TEXT         NOT NULL,
    role            TEXT         NOT NULL,
    student_number  TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'LIBRARIAN', 'ADMIN')),
    CONSTRAINT ck_users_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
    -- Only students have a student number
    CONSTRAINT ck_users_student_number_role CHECK (student_number IS NULL OR role = 'STUDENT')
);

-- Emails are unique regardless of capitalisation: Ann@x.com and ann@x.com are the same person.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uq_users_student_number ON users (student_number) WHERE student_number IS NOT NULL;

CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uq_categories_name_lower ON categories (lower(name));

CREATE TABLE books (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    isbn             TEXT,
    title            TEXT        NOT NULL,
    author           TEXT        NOT NULL,
    publisher        TEXT,
    publication_year INTEGER,
    description      TEXT,
    category_id      BIGINT      NOT NULL REFERENCES categories (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_books_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_books_author_not_blank CHECK (btrim(author) <> ''),
    CONSTRAINT ck_books_publication_year CHECK (publication_year IS NULL OR publication_year BETWEEN 1000 AND 2200)
);

CREATE UNIQUE INDEX uq_books_isbn ON books (isbn) WHERE isbn IS NOT NULL;
CREATE INDEX ix_books_category ON books (category_id);
-- Trigram indexes make "contains" searches on title and author indexable.
CREATE INDEX ix_books_title_trgm ON books USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_books_author_trgm ON books USING gin (lower(author) gin_trgm_ops);

CREATE TABLE book_copies (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id        BIGINT      NOT NULL REFERENCES books (id),
    barcode        TEXT        NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'AVAILABLE',
    shelf_location TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_book_copies_status CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'HELD', 'LOST', 'WITHDRAWN')),
    CONSTRAINT ck_book_copies_barcode_not_blank CHECK (btrim(barcode) <> '')
);

CREATE UNIQUE INDEX uq_book_copies_barcode ON book_copies (barcode);
-- Availability counts: "how many copies of this book are AVAILABLE?"
CREATE INDEX ix_book_copies_book_status ON book_copies (book_id, status);
