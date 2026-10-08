package kg.chairx.expense.application;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException() {
        super("Расход не найден");
    }
}