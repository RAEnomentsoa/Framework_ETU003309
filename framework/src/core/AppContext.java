package core;

public class AppContext {
    private static String webInfPath;

    public static void setWebInfPath(String path) {
        webInfPath = path;
    }

    public static String getWebInfPath() {
        return webInfPath;
    }
}
