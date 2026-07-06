-- ===========================================================================
-- SAANJHA 2.0: V12 MIGRATION (APPLICATION MODULE)
-- FIX TD19 (architecture-review.md §9.2): adds the SEAT_LOST terminal status
-- to app_invitations, the compensating outcome for an accepted invitation
-- that lost a last-slot capacity race in Team before it could be seated.
-- See InvitationStatus.java for the full reasoning on why this is a new
-- status rather than reusing Application's ACCEPTED -> UNDER_REVIEW pattern.
-- ===========================================================================

ALTER TABLE app.app_invitations DROP CONSTRAINT chk_inv_status;

ALTER TABLE app.app_invitations ADD CONSTRAINT chk_inv_status
    CHECK (status IN ('SENT','ACCEPTED','DECLINED','EXPIRED','REVOKED','SEAT_LOST'));
