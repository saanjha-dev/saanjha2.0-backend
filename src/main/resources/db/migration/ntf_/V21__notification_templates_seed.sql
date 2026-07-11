-- ===========================================================================
-- SAANJHA 2.0: V21 MIGRATION (NOTIFICATION MODULE)
-- Seeds real templates for the highest-priority / most user-visible event
-- types across their EMAIL and IN_APP channels. Any (event_type, channel,
-- locale) combination NOT seeded here still dispatches safely -
-- TemplateService.render falls back to a generic, always-available message
-- built from the {{title}} variable every NotificationEventListener call
-- already supplies (see that class). This is a deliberate, bounded v1 seed,
-- not an oversight - authoring the remaining combinations' copy is future,
-- content-team work (see the module's Future Extension Points), not an
-- engineering blocker.
-- ===========================================================================

INSERT INTO ntf.ntf_templates (id, event_type, channel, locale, subject_template, body_template, action_url_template, version, is_active) VALUES
(gen_random_uuid(), 'USER_REGISTERED', 'EMAIL', 'en',
    'Welcome to Saanjha!',
    'Hi there — your account ({{email}}) is verified and ready. Saanjha is built on proof of work, not vanity metrics: find a project, contribute, and let your work speak for itself.',
    NULL, 1, true),
(gen_random_uuid(), 'USER_REGISTERED', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true),

(gen_random_uuid(), 'SUSPICIOUS_ACTIVITY_DETECTED', 'EMAIL', 'en',
    'Security alert: unusual sign-in activity',
    'We detected a suspicious sign-in attempt on your account from IP {{ipAddress}} ({{reason}}). If this wasn''t you, your sessions have been revoked as a precaution - please reset your password.',
    NULL, 1, true),
(gen_random_uuid(), 'SUSPICIOUS_ACTIVITY_DETECTED', 'IN_APP', 'en', NULL,
    'Unusual sign-in activity detected from {{ipAddress}}. Review your account security.', NULL, 1, true),

(gen_random_uuid(), 'APPLICATION_SUBMITTED', 'EMAIL', 'en',
    'New application to review',
    'Someone applied to join your project. Review their application and respond when you''re ready.',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_SUBMITTED', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true),

(gen_random_uuid(), 'APPLICATION_ACCEPTED', 'EMAIL', 'en',
    'You''re in! Your application was accepted',
    'Great news - your application was accepted. You''re now part of the team. Head to the project workspace to get started.',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_ACCEPTED', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true),

(gen_random_uuid(), 'APPLICATION_REJECTED', 'EMAIL', 'en',
    'Update on your application',
    'Thanks for your interest - the project Lead has decided not to move forward with your application this time. Keep exploring other projects on Saanjha.',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_REJECTED', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true),

(gen_random_uuid(), 'MEMBER_REMOVED', 'EMAIL', 'en',
    'You were removed from a team',
    'You''ve been removed from the team. Reason given: {{reason}}. If you believe this was a mistake, reach out to the project Lead.',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBER_REMOVED', 'IN_APP', 'en', NULL, 'Removed from team: {{reason}}', NULL, 1, true),

(gen_random_uuid(), 'MEMBERSHIP_CREATION_REJECTED', 'EMAIL', 'en',
    'We couldn''t confirm your seat on this team',
    'Your acceptance came through, but the last available seat on this team was taken in the same moment ({{reason}}). This isn''t a rejection of you - a Lead will follow up about next steps.',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBERSHIP_CREATION_REJECTED', 'IN_APP', 'en', NULL,
    'Your seat couldn''t be confirmed: {{reason}}. A Lead has been notified.', NULL, 1, true),

(gen_random_uuid(), 'TEAM_ARCHIVED', 'EMAIL', 'en',
    'Your project has wrapped up',
    'The project you contributed to has been marked complete. Your verified contributions are now part of your portfolio - come take a look.',
    '/portfolio', 1, true),
(gen_random_uuid(), 'TEAM_ARCHIVED', 'IN_APP', 'en', NULL, '{{title}}', '/portfolio', 1, true),

(gen_random_uuid(), 'BADGE_AWARDED', 'EMAIL', 'en',
    'You earned a new badge!',
    '{{title}} - your portfolio now reflects this achievement. Nice work.',
    '/portfolio', 1, true),
(gen_random_uuid(), 'BADGE_AWARDED', 'IN_APP', 'en', NULL, '{{title}}', '/portfolio', 1, true),

(gen_random_uuid(), 'CONTRIBUTION_MILESTONE_REACHED', 'EMAIL', 'en',
    'Milestone unlocked',
    '{{title}} - keep it up, your verified track record keeps growing.',
    '/portfolio', 1, true),
(gen_random_uuid(), 'CONTRIBUTION_MILESTONE_REACHED', 'IN_APP', 'en', NULL, '{{title}}', '/portfolio', 1, true),

(gen_random_uuid(), 'INVITATION_SEAT_LOST_INVITEE', 'EMAIL', 'en',
    'About the seat you accepted',
    'You accepted an invitation, but the seat couldn''t be held ({{reason}}). We''re sorry for the mix-up - the project Lead has been notified and may reach out about other openings.',
    NULL, 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_INVITEE', 'IN_APP', 'en', NULL, '{{title}}: {{reason}}', NULL, 1, true),

(gen_random_uuid(), 'INVITATION_SEAT_LOST_LEAD', 'EMAIL', 'en',
    'A seat just opened back up',
    'An accepted invitation couldn''t be seated ({{reason}}), so a spot on your team is open again. Consider inviting someone else.',
    NULL, 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_LEAD', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true),

(gen_random_uuid(), 'PROJECT_COMPLETED', 'EMAIL', 'en',
    'Your project is complete',
    'Congratulations on completing your project! Your team''s contributions are being finalized into verified portfolio entries now.',
    NULL, 1, true),
(gen_random_uuid(), 'PROJECT_COMPLETED', 'IN_APP', 'en', NULL, '{{title}}', NULL, 1, true);
