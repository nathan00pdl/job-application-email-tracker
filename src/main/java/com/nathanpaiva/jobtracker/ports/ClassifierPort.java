package com.nathanpaiva.jobtracker.ports;

import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

/**
 * Reads one email and says what it is about.
 *
 * <p>The interface names no model and no vendor. Whether the answer comes from a large
 * language model, a different one, or one day from rules alone, the application asks the
 * same question and gets the same shape back.
 *
 * <p>One email per call, on purpose. Sending the day's mail in a single request would be
 * cheaper, but a failure would then cost every email at once instead of one — and the
 * pipeline is built to record per-item failures and carry on.
 */
public interface ClassifierPort {

    /**
     * Classifies a single email.
     *
     * <p>Implementations reach an external service, so this can fail. Callers are
     * expected to treat a failure here as a problem with that one email, not with the
     * run.
     */
    ClassificationResult classify(IncomingEmail email);
}
