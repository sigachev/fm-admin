package com.finmates.admin.client.dto;

/**
 * Mirror of fm-social's {@code com.finmates.social.report.ResolutionAction}.
 *
 * <p>This is the wire contract for the {@code resolutionAction} field sent to
 * {@code PUT /api/internal/reports/{id}/resolve} on fm-social. Names MUST match
 * fm-social's enum exactly — Jackson deserializes by name on the receiving end.
 *
 * <p>Mapping from the admin UI action vocabulary
 * ({@link com.finmates.admin.dto.ResolveReportRequest#action()}) to this enum:
 * <ul>
 *   <li>{@code REMOVE_POST}    → {@link #CONTENT_REMOVED}</li>
 *   <li>{@code REMOVE_COMMENT} → {@link #CONTENT_REMOVED}</li>
 *   <li>{@code BAN_USER}       → {@link #USER_BANNED}</li>
 *   <li>{@code DISMISS}        → {@link #NO_ACTION}</li>
 * </ul>
 */
public enum ResolutionAction {
    CONTENT_REMOVED,
    USER_WARNED,
    USER_SUSPENDED,
    USER_BANNED,
    NO_ACTION
}
