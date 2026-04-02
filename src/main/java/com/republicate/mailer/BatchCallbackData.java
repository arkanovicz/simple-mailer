package com.republicate.mailer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class BatchCallbackData
{
    final Object originalData;
    final AtomicInteger remaining;
    final AtomicBoolean failed;

    BatchCallbackData(Object originalData, int batchCount)
    {
        this.originalData = originalData;
        this.remaining = new AtomicInteger(batchCount);
        this.failed = new AtomicBoolean(false);
    }
}
