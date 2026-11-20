-- Study seats and seat bookings.
-- Uses the btree_gist extension enabled in V1.

CREATE TABLE library_seats (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label      TEXT        NOT NULL,
    zone       TEXT        NOT NULL,
    has_power  BOOLEAN     NOT NULL DEFAULT FALSE,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_library_seats_status CHECK (status IN ('ACTIVE', 'OUT_OF_SERVICE')),
    CONSTRAINT ck_library_seats_label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT ck_library_seats_zone_not_blank CHECK (btrim(zone) <> '')
);

CREATE UNIQUE INDEX uq_library_seats_label ON library_seats (lower(label));

CREATE TABLE seat_bookings (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seat_id    BIGINT      NOT NULL REFERENCES library_seats (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'BOOKED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_seat_bookings_status CHECK (status IN ('BOOKED', 'CANCELLED')),
    CONSTRAINT ck_seat_bookings_ends_after_start CHECK (ends_at > starts_at),
    CONSTRAINT ck_seat_bookings_max_duration CHECK (ends_at - starts_at <= interval '8 hours'),

    -- No double booking: two active bookings for the same seat cannot overlap in time.
    -- The range is half-open ([start, end)), so back-to-back bookings (10:00-11:00, 11:00-12:00) are fine.
    -- Cancelled bookings are ignored, so a cancelled slot can be booked again.
    CONSTRAINT ex_seat_bookings_no_seat_overlap
        EXCLUDE USING gist (seat_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (status = 'BOOKED'),

    -- A student cannot be in two places at once: their own active bookings cannot overlap either.
    CONSTRAINT ex_seat_bookings_no_user_overlap
        EXCLUDE USING gist (user_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (status = 'BOOKED')
);

CREATE INDEX ix_seat_bookings_user ON seat_bookings (user_id, starts_at DESC);
