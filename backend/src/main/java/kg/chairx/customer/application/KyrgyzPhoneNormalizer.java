package kg.chairx.customer.application;

/** Normalizes supported Kyrgyz mobile/national formats to nine local digits. */
public final class KyrgyzPhoneNormalizer {
    private KyrgyzPhoneNormalizer() {}

    public static String local(String raw) {
        if(raw==null)return null;
        String digits=raw.replaceAll("[^0-9]","");
        if(digits.startsWith("00996")&&digits.length()==14)digits=digits.substring(2);
        if(digits.startsWith("996")&&digits.length()==12)digits=digits.substring(3);
        else if(digits.startsWith("0")&&digits.length()==10)digits=digits.substring(1);
        if(digits.length()!=9 || !digits.matches("[0-9]{9}"))return null;
        return digits;
    }
}
