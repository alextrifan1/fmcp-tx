package ro.ubb.tickets.txmanager;

public enum LockType {
    READ,
    WRITE;

    public boolean isCompatibleWith(LockType other) {
        return this == READ && other == READ;
    }
}