package com.jd.papernote;

/**
 * Authentication is intentionally not part of PaperNote.
 *
 * PaperNote is an offline-first notebook and does not require an account.
 * This compatibility shell remains so older source references do not break
 * during migration from the experimental Google sign-in builds.
 */
@Deprecated
public final class GoogleAuthManager {
    private GoogleAuthManager() {}
}
