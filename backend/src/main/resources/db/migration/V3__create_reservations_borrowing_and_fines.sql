-- Reservations, borrow records (loans) and fines.

CREATE TABLE reservations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    book_id      BIGINT      NOT NULL REFERENCES books (id),
    -- The physical copy held for the student. Set only once a copy is available (status READY).
    copy_id      BIGINT      REFERENCES book_copies (id),
    status       TEXT        NOT NULL DEFAULT 'PENDING',
    reserved_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Deadline to collect the held copy; after it the reservation expires and the next student is served.
    ready_until  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    -- A READY reservation always names the copy being held and the collection deadline.
    CONSTRAINT ck_reservations_ready_has_copy
        CHECK (status <> 'READY' OR (copy_id IS NOT NULL AND ready_until IS NOT NULL)),
    -- A PENDING reservation is still waiting in the queue, so no copy is assigned.
    CONSTRAINT ck_reservations_pending_has_no_copy CHECK (status <> 'PENDING' OR copy_id IS NULL)
);

-- A student cannot hold two active reservations for the same book,
-- but may reserve it again after cancelling or collecting it.
CREATE UNIQUE INDEX uq_reservations_active_user_book
    ON reservations (user_id, book_id) WHERE status IN ('PENDING', 'READY');
-- A copy can be held for at most one reservation at a time.
CREATE UNIQUE INDEX uq_reservations_ready_copy ON reservations (copy_id) WHERE status = 'READY';
-- The waiting queue for a book, oldest reservation first.
CREATE INDEX ix_reservations_queue ON reservations (book_id, reserved_at) WHERE status = 'PENDING';
CREATE INDEX ix_reservations_user ON reservations (user_id, reserved_at DESC);

CREATE TABLE borrow_records (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    copy_id        BIGINT      NOT NULL REFERENCES book_copies (id),
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    -- The librarian who issued the loan.
    issued_by      BIGINT      NOT NULL REFERENCES users (id),
    -- The reservation this loan fulfils, if the student reserved the book first.
    reservation_id BIGINT      REFERENCES reservations (id),
    borrowed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at         TIMESTAMPTZ NOT NULL,
    -- NULL while the book is still on loan.
    returned_at    TIMESTAMPTZ,
    renewal_count  INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_borrow_records_due_after_borrowed CHECK (due_at > borrowed_at),
    CONSTRAINT ck_borrow_records_returned_after_borrowed CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    CONSTRAINT ck_borrow_records_renewal_count CHECK (renewal_count >= 0)
);

-- A copy can have only one open loan: it cannot be lent to two people at once.
CREATE UNIQUE INDEX uq_borrow_records_open_copy ON borrow_records (copy_id) WHERE returned_at IS NULL;
-- A reservation is fulfilled by at most one loan.
CREATE UNIQUE INDEX uq_borrow_records_reservation ON borrow_records (reservation_id) WHERE reservation_id IS NOT NULL;
-- "My borrowings", newest first.
CREATE INDEX ix_borrow_records_user ON borrow_records (user_id, borrowed_at DESC);
-- The overdue report scans open loans by due date.
CREATE INDEX ix_borrow_records_open_due ON borrow_records (due_at) WHERE returned_at IS NULL;

CREATE TABLE fines (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    borrow_record_id BIGINT         NOT NULL REFERENCES borrow_records (id),
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    amount           NUMERIC(10, 2) NOT NULL,
    status           TEXT           NOT NULL DEFAULT 'UNPAID',
    assessed_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_fines_borrow_record UNIQUE (borrow_record_id),
    CONSTRAINT ck_fines_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_fines_status CHECK (status IN ('UNPAID', 'PAID', 'WAIVED')),
    CONSTRAINT ck_fines_paid_has_date CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

CREATE INDEX ix_fines_user_status ON fines (user_id, status);
