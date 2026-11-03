package ro.ubb.tickets.txmanager;

public class TransactionAbortedException extends RuntimeException {

    private final long transactionId;

    public TransactionAbortedException(long transactionId, String reason) {
        super("T" + transactionId + " aborted: " + reason);
        this.transactionId = transactionId;
    }

    public TransactionAbortedException(long transactionId, String reason, Throwable cause) {
        super("T" + transactionId + " aborted: " + reason, cause);
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }
}

