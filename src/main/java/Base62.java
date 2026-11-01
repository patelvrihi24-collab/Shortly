public final class Base62{
    private static final String CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static String encode(long num){
        if(num == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while(num > 0){
            sb.append(CHARS.charAt((int)(num%62)));
            num/= 62;
        }
        return sb.reverse().toString();
    }
}