package com.nathanpaiva.jobtracker.adapters.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.UpdateType;

/**
 * The shape the model must answer in.
 *
 * <p>The SDK turns this record into a JSON schema and sends it with the request, and the
 * API then constrains the model's output to match. That is a restriction on generation,
 * not a request in the prompt: the answer cannot come back as prose, with a field
 * missing, or with a category outside {@link UpdateType}.
 *
 * <p>The descriptions are not comments. They are sent to the model as part of the
 * schema, so they are instructions about each field and are worth writing carefully.
 *
 * <p>This is a second class describing the same thing as {@link ClassificationResult},
 * for the same reason the JPA entity is separate from the domain record: the domain does
 * not carry Jackson annotations, and the vendor's shape stops here.
 */
record ClaudeClassificationResponse(

        @JsonPropertyDescription("""
                True only if this email is about a job application the recipient has \
                submitted. Job adverts, newsletters, and invitations to apply are not: \
                they are about jobs, but no application exists yet.""")
        boolean relevant,

        @JsonPropertyDescription("""
                The kind of news this email carries. Pick OTHER when the email is about \
                an application but fits none of the other categories.""")
        UpdateType updateType,

        @JsonPropertyDescription("""
                The company doing the hiring, written as it appears in the email. Null \
                if the email does not name one. This is not the hiring platform.""")
        String company,

        @JsonPropertyDescription("""
                The job title, written as it appears in the email. Null if the email \
                does not name one.""")
        String roleTitle,

        @JsonPropertyDescription("""
                The hiring platform the email came through, such as Greenhouse, Gupy or \
                LinkedIn. Null if none is apparent.""")
        String platform,

        @JsonPropertyDescription("""
                One short line saying what happened, in the same language as the email. \
                Null if the email says nothing worth summarising.""")
        String summary,

        @JsonPropertyDescription("""
                True only if the email asks for something time-sensitive, such as \
                confirming a time, answering by a date, or completing a task with a \
                deadline.""")
        boolean urgent
) {

    ClassificationResult toDomain() {
        return new ClassificationResult(
                relevant, updateType, company, roleTitle, platform, summary, urgent);
    }
}
