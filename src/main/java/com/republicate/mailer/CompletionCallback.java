package com.republicate.mailer;

public interface CompletionCallback
{
    public enum Status { DELIVERED, NOT_DELIVERED, PARTIALLY_DELIVERED };
    public void deliveryCompleted(Status status, Object data);
}

