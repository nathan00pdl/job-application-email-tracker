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
     * <p>This is a fallback, and it is here on purpose. The classifier is a language
     * model, so sooner or later it returns something we did not plan for. Without this
     * value, such an email would either stop the run or be saved under an invalid
     * value. If the number of {@code OTHER} rows grows, a category is missing.
     */
    OTHER
}
