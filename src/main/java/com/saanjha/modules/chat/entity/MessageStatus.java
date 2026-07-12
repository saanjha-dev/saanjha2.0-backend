package com.saanjha.modules.chat.entity;

/** SENT is the only non-terminal state; EDITED and DELETED are both still
 * visible in history (DELETED is a soft-delete - body is cleared but the row
 * and its thread/reaction/receipt relationships remain intact, matching the
 * append-only-history convention used elsewhere, e.g. tem_membership_history). */
public enum MessageStatus {
    SENT,
    EDITED,
    DELETED
}
