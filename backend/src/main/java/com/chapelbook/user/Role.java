package com.chapelbook.user;

/** The three portals of ChapelBook. The name is also the authority checked by Spring Security (ROLE_x). */
public enum Role {
    STUDENT,
    LIBRARIAN,
    ADMIN
}
