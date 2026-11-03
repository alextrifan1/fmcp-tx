package ro.ubb.tickets.txmanager;

public enum OperationType {
    SELECT(LockType.READ),
    INSERT(LockType.WRITE),
    UPDATE(LockType.WRITE),
    DELETE(LockType.WRITE);

    private final LockType requiredLock;

    OperationType(LockType requiredLock) {
        this.requiredLock = requiredLock;
    }

    public LockType requiredLock() {
        return requiredLock;
    }
}