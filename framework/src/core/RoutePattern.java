package core;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

public class RoutePattern {
    public String original;
    public Pattern regex;
    public List<String> paramNames = new ArrayList<>();
    public Method method;
    public Object controller;

    public RoutePattern(String path, Method method, Object controller) {
        this.original = path;
        this.method = method;
        this.controller = controller;

        // Extract parameter names
        Matcher m = Pattern.compile("\\{([^/]+)}").matcher(path);
        while (m.find()) {
            paramNames.add(m.group(1));
        }

        // Convert "{something}" into "([^/]+)"
        String regexString = path.replaceAll("\\{[^/]+}", "([^/]+)");

        this.regex = Pattern.compile("^" + regexString + "$");
    }

    public Map<String, String> match(String url) {
        Matcher m = regex.matcher(url);

        if (!m.matches())
            return null;

        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < paramNames.size(); i++) {
            params.put(paramNames.get(i), m.group(i + 1));
        }
        return params;
    }
}
