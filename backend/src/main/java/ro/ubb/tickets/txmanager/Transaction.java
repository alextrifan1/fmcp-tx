package ro.ubb.tickets.txmanager;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class Transaction {

    private final long id;
    private final Instant timestamp;
    private final AtomicReference<TransactionStatus> status = new AtomicReference<>(TransactionStatus.ACTIVE);

    private final List<Operation> operations = new CopyOnWriteArrayList<>();

    private final Deque<UndoEntry> undoLog = new ArrayDeque<>();

    Transaction(long id) { //only TransactionManager creates transactions
        this.id = id;
        this.timestamp = Instant.now();
    }

    public long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public TransactionStatus getStatus() {
        return status.get();
    }

    public boolean isActive() {
        return status.get() == TransactionStatus.ACTIVE;
    }

    public List<Operation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    //

    // Active -> Committed
    boolean markCommitted() {
        return status.compareAndSet(TransactionStatus.ACTIVE, TransactionStatus.COMMIT);
    }

    // Active -> Abort
    boolean markAborted() {
        return status.compareAndSet(TransactionStatus.ACTIVE, TransactionStatus.ABORT);
    }

    void ensureActive() {
        TransactionStatus s = status.get();
        if (s != TransactionStatus.ACTIVE) {
            throw new TransactionAbortedException(id, "transaction is not active (status = " + s + ")");
        }
    }

    /** Înregistrează o operație executată și compensarea ei (null pentru SELECT). */
    void recordExecuted(Operation op, UndoEntry undo) {
        operations.add(op);
        if (undo != null) {
            undoLog.push(undo);
        }
    }

    UndoEntry popUndo() {
        return undoLog.poll();
    }

    void clearUndoLog() {
        undoLog.clear();
    }

    int undoLogSize() {
        return undoLog.size();
    }

    @Override
    public String toString() {
        return "T" + id + "[" + status.get() + ", ops=" + operations.size() + "]";
    }

}
