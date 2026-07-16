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
--
-- EMAIL bodies below are complete, production-grade HTML documents (the
-- enterprise email design system redesign) generated from a single shared
-- layout - see /docs/notification/email-design-system.md and
-- scripts/notification/gen_templates.py, the source of truth for this file.
-- Do not hand-edit the HTML in this file; edit the generator and re-run it,
-- or every template will silently drift out of sync with the design system.
-- IN_APP bodies are untouched, lightweight plain text, same as before.
-- ===========================================================================

INSERT INTO ntf.ntf_templates (id, event_type, channel, locale, subject_template, body_template, action_url_template, version, is_active) VALUES
(gen_random_uuid(), 'USER_REGISTERED', 'EMAIL', 'en',
    'Welcome to Saanjha!',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Welcome to Saanjha!</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Your account ({{email}}) is verified - find a project and let your work speak for itself.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#2563EB;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#EFF6FF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#2563EB" style="width:52px;height:52px;border-radius:26px;background-color:#2563EB;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Welcome">
                  &#128075;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#2563EB;margin-top:14px;">
              Account
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              Welcome to Saanjha!
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Your account is verified and ready to go.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Hi there &mdash; your account (<strong>{{email}}</strong>) is verified and ready.</p><p style="margin:0 0 12px;">Saanjha is built on proof of work, not vanity metrics: find a project, contribute, and let your work speak for itself.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#2563EB">
                  <a href="/projects" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#2563EB;padding:12px 24px;border-radius:8px;">
                    Explore Projects &rarr;
                  </a>
                </td>
                <td class="stack-col" align="center" style="padding-bottom:8px;">
                  <a href="/dashboard" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#111827;background-color:#FFFFFF;border:1px solid #E5E7EB;padding:11px 23px;border-radius:8px;">
                    Open Dashboard
                  </a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#2563EB;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#2563EB;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#2563EB;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'USER_REGISTERED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true),
(gen_random_uuid(), 'SUSPICIOUS_ACTIVITY_DETECTED', 'EMAIL', 'en',
    'Security alert: unusual sign-in activity',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Security alert: unusual sign-in activity</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  We detected a suspicious sign-in attempt on your account. Review it now.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#DC2626;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#FEF2F2;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#DC2626" style="width:52px;height:52px;border-radius:26px;background-color:#DC2626;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Warning">
                  &#9888;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#DC2626;margin-top:14px;">
              Security
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              Unusual sign-in activity detected
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              We took precautionary action on your account.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">We detected a suspicious sign-in attempt on your account.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="info-box border-soft" style="background-color:#FEF2F2;border:1px solid #E5E7EB;border-radius:8px;margin-top:14px;">
                    <tr>
                      <td style="padding:14px 16px;">
                        <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">IP address:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{ipAddress}}</span>
                        </div><div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">Reason:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{reason}}</span>
                        </div>
                      </td>
                    </tr>
                  </table><p style="margin:0 0 12px;">If this wasn&rsquo;t you, your sessions have been revoked as a precaution &mdash; please reset your password.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#DC2626">
                  <a href="/settings/security" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#DC2626;padding:12px 24px;border-radius:8px;">
                    Reset Password &rarr;
                  </a>
                </td>
                <td class="stack-col" align="center" style="padding-bottom:8px;">
                  <a href="/settings/security/activity" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#111827;background-color:#FFFFFF;border:1px solid #E5E7EB;padding:11px 23px;border-radius:8px;">
                    Review Activity
                  </a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#DC2626;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#DC2626;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#DC2626;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'SUSPICIOUS_ACTIVITY_DETECTED', 'IN_APP', 'en',
    NULL,
    'Unusual sign-in activity detected from {{ipAddress}}. Review your account security.',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_SUBMITTED', 'EMAIL', 'en',
    'New application to review',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>New application to review</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Someone applied to join your project. Review their application and respond when ready.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#4F46E5;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#EEF2FF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#4F46E5" style="width:52px;height:52px;border-radius:26px;background-color:#4F46E5;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Application">
                  &#128221;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#4F46E5;margin-top:14px;">
              Application
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              New application to review
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Someone applied to join your project.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Someone applied to join your project. Review their application and respond when you&rsquo;re ready.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#4F46E5">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#4F46E5;padding:12px 24px;border-radius:8px;">
                    Review Application &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#4F46E5;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#4F46E5;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#4F46E5;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'APPLICATION_SUBMITTED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_ACCEPTED', 'EMAIL', 'en',
    'You''re in! Your application was accepted',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>You''re in! Your application was accepted</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Great news - your application was accepted. Head to the project workspace to get started.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#4F46E5;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#EEF2FF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#4F46E5" style="width:52px;height:52px;border-radius:26px;background-color:#4F46E5;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Accepted">
                  &#9989;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#4F46E5;margin-top:14px;">
              Application
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              You''re in!
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Your application was accepted.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Great news &mdash; your application was accepted. You&rsquo;re now part of the team.</p><p style="margin:0 0 12px;">Head to the project workspace to get started.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#4F46E5">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#4F46E5;padding:12px 24px;border-radius:8px;">
                    Open Workspace &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#4F46E5;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#4F46E5;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#4F46E5;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'APPLICATION_ACCEPTED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_REJECTED', 'EMAIL', 'en',
    'Update on your application',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Update on your application</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Thanks for your interest - keep exploring other projects on Saanjha.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#4F46E5;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#EEF2FF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#4F46E5" style="width:52px;height:52px;border-radius:26px;background-color:#4F46E5;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Update">
                  &#8505;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#4F46E5;margin-top:14px;">
              Application
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              Update on your application
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              This one didn''t move forward.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Thanks for your interest &mdash; the project Lead has decided not to move forward with your application this time.</p><p style="margin:0 0 12px;">Keep exploring other projects on Saanjha; the right fit is often one application away.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#4F46E5">
                  <a href="/projects" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#4F46E5;padding:12px 24px;border-radius:8px;">
                    Explore Projects &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#4F46E5;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#4F46E5;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#4F46E5;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'APPLICATION_REJECTED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBER_REMOVED', 'EMAIL', 'en',
    'You were removed from a team',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>You were removed from a team</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  You''ve been removed from the team. If you believe this was a mistake, reach out to the Lead.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#0E7490;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#ECFEFF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#0E7490" style="width:52px;height:52px;border-radius:26px;background-color:#0E7490;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Team update">
                  &#128100;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#0E7490;margin-top:14px;">
              Team
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              You were removed from a team
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Here''s what was shared with us.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">You&rsquo;ve been removed from the team.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="info-box border-soft" style="background-color:#ECFEFF;border:1px solid #E5E7EB;border-radius:8px;margin-top:14px;">
                    <tr>
                      <td style="padding:14px 16px;">
                        <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">Reason:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{reason}}</span>
                        </div>
                      </td>
                    </tr>
                  </table><p style="margin:0 0 12px;">If you believe this was a mistake, reach out to the project Lead.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#0E7490">
                  <a href="mailto:support@saanjha.dev" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#0E7490;padding:12px 24px;border-radius:8px;">
                    Contact Support &rarr;
                  </a>
                </td>
                <td class="stack-col" align="center" style="padding-bottom:8px;">
                  <a href="/projects" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#111827;background-color:#FFFFFF;border:1px solid #E5E7EB;padding:11px 23px;border-radius:8px;">
                    Explore Projects
                  </a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#0E7490;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#0E7490;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#0E7490;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBER_REMOVED', 'IN_APP', 'en',
    NULL,
    'Removed from team: {{reason}}',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBERSHIP_CREATION_REJECTED', 'EMAIL', 'en',
    'We couldn''t confirm your seat on this team',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>We couldn''t confirm your seat on this team</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Your acceptance came through, but the last available seat was taken in the same moment.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#0E7490;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#ECFEFF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#0E7490" style="width:52px;height:52px;border-radius:26px;background-color:#0E7490;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Notice">
                  &#9888;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#0E7490;margin-top:14px;">
              Team
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              We couldn''t confirm your seat
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              This isn''t a rejection of you.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Your acceptance came through, but the last available seat on this team was taken in the same moment.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="info-box border-soft" style="background-color:#ECFEFF;border:1px solid #E5E7EB;border-radius:8px;margin-top:14px;">
                    <tr>
                      <td style="padding:14px 16px;">
                        <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">Reason:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{reason}}</span>
                        </div>
                      </td>
                    </tr>
                  </table><p style="margin:0 0 12px;">This isn&rsquo;t a rejection of you &mdash; a Lead will follow up about next steps.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#0E7490">
                  <a href="/projects" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#0E7490;padding:12px 24px;border-radius:8px;">
                    Explore Projects &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#0E7490;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#0E7490;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#0E7490;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'MEMBERSHIP_CREATION_REJECTED', 'IN_APP', 'en',
    NULL,
    'Your seat couldn''t be confirmed: {{reason}}. A Lead has been notified.',
    NULL, 1, true),
(gen_random_uuid(), 'TEAM_ARCHIVED', 'EMAIL', 'en',
    'Your project has wrapped up',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Your project has wrapped up</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  The project you contributed to is complete - your verified contributions are ready to view.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#0E7490;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#ECFEFF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#0E7490" style="width:52px;height:52px;border-radius:26px;background-color:#0E7490;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Completed">
                  &#127937;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#0E7490;margin-top:14px;">
              Team
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              Your project has wrapped up
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Your contributions are now part of your portfolio.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">The project you contributed to has been marked complete.</p><p style="margin:0 0 12px;">Your verified contributions are now part of your portfolio &mdash; come take a look.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#0E7490">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#0E7490;padding:12px 24px;border-radius:8px;">
                    View Portfolio &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#0E7490;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#0E7490;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#0E7490;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'TEAM_ARCHIVED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    '/portfolio', 1, true),
(gen_random_uuid(), 'BADGE_AWARDED', 'EMAIL', 'en',
    'You earned a new badge!',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>You earned a new badge!</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  {{title}} - nice work. Your portfolio now reflects this achievement.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#B45309;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#FFFBEB;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#B45309" style="width:52px;height:52px;border-radius:26px;background-color:#B45309;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Badge earned">
                  &#127942;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#B45309;margin-top:14px;">
              Portfolio
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              {{title}}
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Your portfolio now reflects this achievement.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;"><strong>{{title}}</strong> &mdash; your portfolio now reflects this achievement. Nice work.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#B45309">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#B45309;padding:12px 24px;border-radius:8px;">
                    View Achievement &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#B45309;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#B45309;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#B45309;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'BADGE_AWARDED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    '/portfolio', 1, true),
(gen_random_uuid(), 'CONTRIBUTION_MILESTONE_REACHED', 'EMAIL', 'en',
    'Milestone unlocked',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Milestone unlocked</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  {{title}} - keep it up, your verified track record keeps growing.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#16A34A;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#F0FDF4;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#16A34A" style="width:52px;height:52px;border-radius:26px;background-color:#16A34A;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Milestone">
                  &#128200;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#16A34A;margin-top:14px;">
              Contribution
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              {{title}}
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Your verified track record keeps growing.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;"><strong>{{title}}</strong> &mdash; keep it up, your verified track record keeps growing.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#16A34A">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#16A34A;padding:12px 24px;border-radius:8px;">
                    View Contribution &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#16A34A;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#16A34A;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#16A34A;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'CONTRIBUTION_MILESTONE_REACHED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    '/portfolio', 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_INVITEE', 'EMAIL', 'en',
    'About the seat you accepted',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>About the seat you accepted</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  You accepted an invitation, but the seat couldn''t be held. We''re sorry for the mix-up.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#0E7490;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#ECFEFF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#0E7490" style="width:52px;height:52px;border-radius:26px;background-color:#0E7490;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Notice">
                  &#8505;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#0E7490;margin-top:14px;">
              Team
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              About the seat you accepted
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              We''re sorry for the mix-up.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">You accepted an invitation, but the seat couldn&rsquo;t be held.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="info-box border-soft" style="background-color:#ECFEFF;border:1px solid #E5E7EB;border-radius:8px;margin-top:14px;">
                    <tr>
                      <td style="padding:14px 16px;">
                        <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">Reason:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{reason}}</span>
                        </div>
                      </td>
                    </tr>
                  </table><p style="margin:0 0 12px;">We&rsquo;re sorry for the mix-up &mdash; the project Lead has been notified and may reach out about other openings.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#0E7490">
                  <a href="/projects" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#0E7490;padding:12px 24px;border-radius:8px;">
                    Explore Projects &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#0E7490;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#0E7490;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#0E7490;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_INVITEE', 'IN_APP', 'en',
    NULL,
    '{{title}}: {{reason}}',
    NULL, 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_LEAD', 'EMAIL', 'en',
    'A seat just opened back up',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>A seat just opened back up</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  An accepted invitation couldn''t be seated, so a spot on your team is open again.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#0E7490;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#ECFEFF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#0E7490" style="width:52px;height:52px;border-radius:26px;background-color:#0E7490;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Team update">
                  &#128101;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#0E7490;margin-top:14px;">
              Team
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              A seat just opened back up
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Consider inviting someone else.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">An accepted invitation couldn&rsquo;t be seated, so a spot on your team is open again.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="info-box border-soft" style="background-color:#ECFEFF;border:1px solid #E5E7EB;border-radius:8px;margin-top:14px;">
                    <tr>
                      <td style="padding:14px 16px;">
                        <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;">
                          <span class="text-secondary" style="color:#4B5563;">Reason:</span>
                          <span class="text-primary" style="color:#111827;font-weight:600;">{{reason}}</span>
                        </div>
                      </td>
                    </tr>
                  </table><p style="margin:0 0 12px;">Consider inviting someone else.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#0E7490">
                  <a href="/team" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#0E7490;padding:12px 24px;border-radius:8px;">
                    Open Team &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#0E7490;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#0E7490;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#0E7490;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    NULL, 1, true),
(gen_random_uuid(), 'INVITATION_SEAT_LOST_LEAD', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true),
(gen_random_uuid(), 'PROJECT_COMPLETED', 'EMAIL', 'en',
    'Your project is complete',
    '<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Your project is complete</title>
<!--[if mso]>
<noscript>
<xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml>
</noscript>
<style>
  table { border-collapse: collapse; }
  .fallback-font { font-family: Arial, Helvetica, sans-serif !important; }
</style>
<![endif]-->
<style>
  body, table, td { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
  img { border: 0; line-height: 100%; outline: none; text-decoration: none; -ms-interpolation-mode: bicubic; }
  a { text-decoration: none; }
  @media screen and (max-width: 600px) {
    .email-container { width: 100% !important; max-width: 100% !important; }
    .stack-col { display: block !important; width: 100% !important; }
    .mobile-px { padding-left: 20px !important; padding-right: 20px !important; }
    .mobile-center { text-align: center !important; }
    .btn-table { width: 100% !important; }
    .btn-link { display: block !important; width: 100% !important; text-align: center !important; box-sizing: border-box; }
  }
  @media (prefers-color-scheme: dark) {
    .bg-body { background-color: #0B0F19 !important; }
    .bg-card { background-color: #161B26 !important; }
    .text-primary { color: #F3F4F6 !important; }
    .text-secondary { color: #9CA3AF !important; }
    .border-soft { border-color: #263041 !important; }
    .bg-hero { background-color: #1B2231 !important; }
    .info-box { background-color: #1B2231 !important; border-color: #263041 !important; }
  }
</style>
</head>
<body class="bg-body" style="margin:0;padding:0;background-color:#F8FAFC;">
<div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#F8FAFC;opacity:0;">
  Congratulations on completing your project! Contributions are being finalized now.&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="bg-body" style="background-color:#F8FAFC;">
<tr>
<td align="center" style="padding:32px 16px;">

<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="max-width:600px;width:100%;">
  <tr>
    <td align="center" style="padding-bottom:20px;">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:18px;font-weight:700;color:#111827;letter-spacing:-0.2px;" class="text-primary">
            Saanjha
          </td>
        </tr>
      </table>
      <div style="height:3px;width:44px;background-color:#7C3AED;margin:10px auto 0;border-radius:2px;font-size:0;line-height:0;">&nbsp;</div>
    </td>
  </tr>

  <tr>
    <td class="bg-card border-soft" style="background-color:#FFFFFF;border:1px solid #E5E7EB;border-radius:12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">

        <tr>
          <td class="bg-hero mobile-px" align="center" style="background-color:#F5F3FF;border-radius:12px 12px 0 0;padding:32px 32px 24px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="52" height="52" align="center" valign="middle" bgcolor="#7C3AED" style="width:52px;height:52px;border-radius:26px;background-color:#7C3AED;font-family:Arial,sans-serif;font-size:22px;line-height:52px;color:#FFFFFF;text-align:center;" role="img" aria-label="Project complete">
                  &#127881;
                </td>
              </tr>
            </table>
            <div style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#7C3AED;margin-top:14px;">
              Project
            </div>
            <h1 class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:22px;line-height:28px;font-weight:700;color:#111827;margin:8px 0 0;">
              Your project is complete
            </h1>
            <p class="text-secondary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;line-height:20px;color:#4B5563;margin:6px 0 0;">
              Contributions are being finalized into your portfolio.
            </p>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:28px 32px 8px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td class="text-primary" style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:15px;line-height:23px;color:#111827;">
                  <p style="margin:0 0 12px;">Congratulations on completing your project! Your team&rsquo;s contributions are being finalized into verified portfolio entries now.</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:4px 32px 20px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="btn-table">
              <tr>
                <td class="stack-col" align="center" style="padding-right:10px;padding-bottom:8px;" bgcolor="#7C3AED">
                  <a href="{{actionUrl}}" class="btn-link" style="display:inline-block;font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:14px;font-weight:600;color:#FFFFFF;background-color:#7C3AED;padding:12px 24px;border-radius:8px;">
                    View Project &rarr;
                  </a>
                </td>
                
              </tr>
            </table>
          </td>
        </tr>

        <tr>
          <td class="mobile-px" style="padding:8px 32px 28px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="border-soft" style="border-top:1px solid #E5E7EB;">
              <tr><td style="font-size:1px;line-height:1px;">&nbsp;</td></tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>

  <tr>
    <td align="center" style="padding:24px 24px 0;">
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:0;">
        Questions? <a href="mailto:support@saanjha.dev" style="color:#7C3AED;">support@saanjha.dev</a>
        &nbsp;&middot;&nbsp;
        <a href="/settings/notifications" style="color:#7C3AED;">Notification settings</a>
        &nbsp;&middot;&nbsp;
        <a href="/privacy" style="color:#7C3AED;">Privacy</a>
      </p>
      <p style="font-family:''Segoe UI'',Helvetica,Arial,sans-serif;font-size:12px;line-height:18px;color:#9CA3AF;margin:10px 0 0;">
        &copy; 2026 Saanjha. Built on proof of work.
      </p>
    </td>
  </tr>
</table>

</td>
</tr>
</table>
</body>
</html>',
    '{{actionUrl}}', 1, true),
(gen_random_uuid(), 'PROJECT_COMPLETED', 'IN_APP', 'en',
    NULL,
    '{{title}}',
    NULL, 1, true);
