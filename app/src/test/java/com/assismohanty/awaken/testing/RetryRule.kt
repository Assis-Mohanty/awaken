package com.assismohanty.awaken.testing

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Re-runs a test up to [attempts] times and fails with the last error. Only for tests that race a framework we don't
 * control (WorkManager's test executor); a failure that repeats every time still fails.
 */
class RetryRule(private val attempts: Int = 3) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            var last: Throwable? = null
            repeat(attempts) {
                try {
                    base.evaluate()
                    return
                } catch (e: Throwable) {
                    last = e
                }
            }
            throw checkNotNull(last)
        }
    }
}
