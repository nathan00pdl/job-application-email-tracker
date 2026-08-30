package com.nathanpaiva.jobtracker.domain;

/**
 * The kind of news an email carries about a job application.
 *
 * <p>This says what the email <em>is</em>, not what stage the application is in.
 * "We would like to interview you" is an {@link #INTERVIEW_INVITE} whether it is the
 * first interview or the third. The stage of a process comes from the sequence of
 * emails over time, and is tracked separately.
 *
 * <p>The database column is a {@code VARCHAR}, not a native enum, so adding a new
 * category never needs a migration. The cost is that the list of allowed values is
 * checked here in the code instead of by the database.
 */
public enum UpdateType {

    /** An automatic reply confirming that an application was received. */
    APPLICATION_RECEIVED,

    /** A coding challenge, take-home task or technical test to complete. */
    TECHNICAL_TEST,

    /** An invitation to an interview, or an email scheduling one. */
    INTERVIEW_INVITE,

    /** A request for information: documents, availability, salary expectations. */
    INFO_REQUEST,

    /** The application did not move forward. */
    REJECTION,

    /** A job offer. */
    OFFER,

    /**
     * About a job application, but none of the categories above.
     *
     * <p>This is a fallback for emails that genuinely fit nowhere else — a scheduling
     * change, a note from a recruiter that carries no news. It is not there to catch bad
     * output: the classifier answers against a schema that lists these seven values, so
     * a category outside the list cannot come back.
     *
     * <p>A growing number of {@code OTHER} rows means a category is missing.
     */
    OTHER
}
