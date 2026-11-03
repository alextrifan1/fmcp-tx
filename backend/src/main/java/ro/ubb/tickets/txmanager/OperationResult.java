package ro.ubb.tickets.txmanager;

import java.util.List;
import java.util.Map;

// result of a single operation
public record OperationResult(
        List<Map<String, Object>> rows,
        Long generatedId,
        int affectedRows
) {
    static OperationResult ofRows(List<Map<String, Object>> rows) {
        return new OperationResult(List.copyOf(rows), null, rows.size());
    }

    static OperationResult ofUpdate(int affectedRows) {
        return new OperationResult(List.of(), null, affectedRows);
    }

    static OperationResult ofInsert(Long id) {
        return new OperationResult(List.of(), id, 1);
    }

    // first row of a select or null
    public Map<String, Object> firstRow() {
        return rows.isEmpty() ? null : rows.get(0);
    }
}
