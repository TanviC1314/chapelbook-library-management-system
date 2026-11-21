package com.chapelbook.reservation;

import jakarta.validation.constraints.NotNull;

public record ReserveRequest(@NotNull Long bookId) {
}
