package kg.chairx.purchase.application;

public class PurchaseNotFoundException extends RuntimeException {
    public PurchaseNotFoundException() { super("Закупка или поступление не найдены"); }
}
