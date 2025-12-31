package core;

import core.annotation.Controller;
import core.annotation.RestAPI;
import core.annotation.Route;
import core.rest.ApiResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import jakarta.servlet.ServletException;

public class RouterServlet extends HttpServlet {

    // New dynamic route system
    public final java.util.List<RoutePattern> routePatterns = new java.util.ArrayList<>();

    @Override
    public void init() {
        System.out.println("Router initialized");
        String basePackage = "app.controllers";
        String path = getServletContext().getRealPath("/WEB-INF/classes/" + basePackage.replace('.', '/'));

        File directory = new File(path);
        if (!directory.exists()) {
            System.out.println("Controllers directory not found: " + path);
            return;
        }

        scanAndRegisterControllers(directory, basePackage);
    }

    private void scanAndRegisterControllers(File directory, String basePackage) {
        File[] files = directory.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanAndRegisterControllers(file, basePackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {

                String className = file.getName().replace(".class", "");

                try {
                    Class<?> clazz = Class.forName(basePackage + "." + className);

                    if (clazz.isAnnotationPresent(core.annotation.Controller.class)) {
                        Object controller = clazz.getDeclaredConstructor().newInstance();
                        registerController(controller);

                        System.out.println("Registered controller: " + clazz.getName());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void registerController(Object controller) {
        Class<?> clazz = controller.getClass();

        if (!clazz.isAnnotationPresent(Controller.class))
            return;

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class)) {
                String path = method.getAnnotation(Route.class).value();
                String httpMethod = method.getAnnotation(Route.class).method();
                routePatterns.add(new RoutePattern(path, method, controller, httpMethod));

                System.out.println("Registered route: " + path + " → " + method.getName());
            }
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Handle PATCH explicitly (some servlet containers don't dispatch it to
        // doPatch)
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handleRequest(req, resp);
            return;
        }

        super.service(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // head = same as get but without body (we manage in handleRequest)
        handleRequest(req, resp);
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI().replace(req.getContextPath(), "");
        String requestMethod = req.getMethod().toUpperCase();

        RoutePattern matchedByPath = null;
        RoutePattern matchedByMethod = null;
        Map<String, String> paramsForMethod = null;

        // Collect allowed methods for this path (for 405 + Allow header)
        java.util.Set<String> allowedMethods = new java.util.LinkedHashSet<>();

        for (RoutePattern rp : routePatterns) {
            Map<String, String> p = rp.match(path);
            if (p != null) {
                matchedByPath = rp;
                allowedMethods.add(rp.httpMethod.toUpperCase());

                if (rp.httpMethod.equalsIgnoreCase(requestMethod)) {
                    matchedByMethod = rp;
                    paramsForMethod = p;
                    break;
                }
            }
        }

        // 404 if no path matches
        // 404 if no path matches
        if (matchedByPath == null) {
            if (isApiPath(path)) {
                writeJsonError(resp, 404, "Not Found: " + path);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("404 - Not Found: " + path);
            }
            return;
        }

        // Auto support OPTIONS: return Allow
        if ("OPTIONS".equals(requestMethod)) {
            // Optional: also include HEAD if GET exists
            if (allowedMethods.contains("GET"))
                allowedMethods.add("HEAD");
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            resp.setHeader("Allow", String.join(", ", allowedMethods));
            return;
        }

        // Auto support HEAD if GET exists (common framework behavior)
        if ("HEAD".equals(requestMethod) && allowedMethods.contains("GET")) {
            // Treat as GET but don't write body
            requestMethod = "GET";
            // Try again to find GET handler
            for (RoutePattern rp : routePatterns) {
                Map<String, String> p = rp.match(path);
                if (p != null && rp.httpMethod.equalsIgnoreCase("GET")) {
                    matchedByMethod = rp;
                    paramsForMethod = p;
                    break;
                }
            }
        }

        // 405 if path exists but method doesn't
        if (matchedByMethod == null) {
            if (allowedMethods.contains("GET"))
                allowedMethods.add("HEAD");

            resp.setHeader("Allow", String.join(", ", allowedMethods));

            if (isApiPath(path)) {
                writeJsonError(resp, 405, "Method " + requestMethod + " Not Allowed on " + path);
            } else {
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                resp.getWriter().write("405 - Method " + requestMethod + " Not Allowed on " + path);
            }
            return;
        }

        // Invoke controller
        try {
            Object result;

            int pc = matchedByMethod.method.getParameterCount();

            if (pc == 1 && Map.class.isAssignableFrom(matchedByMethod.method.getParameterTypes()[0])) {
                // Sprint 8 (Map) — tu gardes ton comportement existant
                result = matchedByMethod.method.invoke(matchedByMethod.controller, paramsForMethod);

            } else if (pc == 1) {
                // Sprint 8-bis (Value Object / POJO)
                Class<?> paramType = matchedByMethod.method.getParameterTypes()[0];

                // On exclut les types simples déjà gérés ailleurs
                if (paramType != HttpServletRequest.class && paramType != HttpServletResponse.class) {
                    Object obj = buildObjectFromRequest(paramType, req);
                    result = matchedByMethod.method.invoke(matchedByMethod.controller, obj);
                } else {
                    Object[] methodArgs = injectParameters(matchedByMethod.method, req, paramsForMethod);
                    result = matchedByMethod.method.invoke(matchedByMethod.controller, methodArgs);
                }

            } else {
                // Sprint 6/7 — injection classique
                Object[] methodArgs = injectParameters(matchedByMethod.method, req, paramsForMethod);
                result = matchedByMethod.method.invoke(matchedByMethod.controller, methodArgs);
            }

            // Api rest

            boolean isRest = matchedByMethod.controller.getClass().isAnnotationPresent(RestAPI.class)
                    || matchedByMethod.method.isAnnotationPresent(RestAPI.class);

            if (isRest) {
                resp.setContentType("application/json;charset=UTF-8");
                resp.setStatus(HttpServletResponse.SC_OK);

                ApiResponse api = new ApiResponse(200, "success", result);
                resp.getWriter().write(toJson(api));
                return;
            }

            // If HEAD: never send body
            boolean isHead = "HEAD".equalsIgnoreCase(req.getMethod());
            if (isHead) {
                resp.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            if (result instanceof ModelView mv) {
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                req.getRequestDispatcher("/WEB-INF/views/" + mv.getView()).forward(req, resp);
                return;
            }

            if (result instanceof String str) {
                resp.getWriter().write(str);
                return;
            }

            resp.getWriter().write("Unsupported return type from controller");

        } catch (Exception e) {
            e.printStackTrace();

            boolean isRest = matchedByMethod != null &&
                    (matchedByMethod.controller.getClass().isAnnotationPresent(RestAPI.class)
                            || matchedByMethod.method.isAnnotationPresent(RestAPI.class));

            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            if (isRest) {
                resp.setContentType("application/json;charset=UTF-8");
                ApiResponse api = new ApiResponse(500, "error", e.getMessage());
                resp.getWriter().write(toJson(api));
            } else {
                resp.getWriter().write("500 - Server error: " + e.getMessage());
            }
        }

    }

    private Map<String, Object> getValues(HttpServletRequest req) {
        Map<String, Object> data = new java.util.HashMap<>();

        // Servlet gives Map<String, String[]> directly
        Map<String, String[]> raw = req.getParameterMap();

        for (Map.Entry<String, String[]> e : raw.entrySet()) {
            String key = e.getKey();
            String[] values = e.getValue();

            if (values == null)
                continue;

            if (values.length == 1) {
                data.put(key, values[0]); // single input => String
            } else {
                // checkbox / multi-select => List<String> (value object style)
                data.put(key, java.util.Arrays.asList(values));
            }
        }
        return data;
    }

    private Object buildObjectFromRequest(Class<?> clazz, HttpServletRequest req) throws Exception {
        Object obj = clazz.getDeclaredConstructor().newInstance();

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            String name = field.getName();
            Class<?> type = field.getType();

            // Always use parameterValues first (supports checkbox/multi-select)
            String[] values = req.getParameterValues(name);
            if (values == null)
                continue;

            // ---- MULTI VALUES ----
            if (java.util.List.class.isAssignableFrom(type)) {
                field.set(obj, java.util.Arrays.asList(values));
                continue;
            }

            if (type.isArray() && type.getComponentType() == String.class) {
                field.set(obj, values);
                continue;
            }

            // ---- SINGLE VALUE ----
            String raw = (values.length > 0) ? values[0] : null;
            if (raw == null)
                continue;

            Object converted;

            if (type == String.class) {
                converted = raw;
            } else if (type == int.class || type == Integer.class) {
                converted = Integer.parseInt(raw);
            } else if (type == long.class || type == Long.class) {
                converted = Long.parseLong(raw);
            } else if (type == double.class || type == Double.class) {
                converted = Double.parseDouble(raw);
            } else if (type == boolean.class || type == Boolean.class) {
                // checkbox often sends "on"
                converted = raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equals("1");
            } else {
                // fallback
                converted = raw;
            }

            field.set(obj, converted);
        }

        return obj;
    }

    // ------------- REST JSON --------------------

    private String toJson(Object obj) {
        if (obj == null)
            return "null";

        if (obj instanceof String s)
            return "\"" + escapeJson(s) + "\"";
        if (obj instanceof Number || obj instanceof Boolean)
            return obj.toString();

        if (obj instanceof java.util.Map<?, ?> map)
            return toJsonMap(map);

        if (obj instanceof Iterable<?> it)
            return toJsonIterable(it);

        if (obj.getClass().isArray())
            return toJsonArray(obj);

        return toJsonObject(obj);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String toJsonMap(java.util.Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(String.valueOf(e.getKey()))).append("\":");
            sb.append(toJson(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonIterable(Iterable<?> it) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object v : it) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append(toJson(v));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonArray(Object arr) {
        int len = java.lang.reflect.Array.getLength(arr);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(toJson(java.lang.reflect.Array.get(arr, i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonObject(Object obj) {
        // POJO -> JSON via fields
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(obj);
                if (!first)
                    sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(f.getName())).append("\":");
                sb.append(toJson(val));
            } catch (Exception ignored) {
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private boolean isApiPath(String path) {
        return path != null && path.startsWith("/api");
    }

    private void writeJsonError(HttpServletResponse resp, int code, String msg) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json;charset=UTF-8");
        core.rest.ApiResponse api = new core.rest.ApiResponse(code, "error", msg);
        resp.getWriter().write(toJson(api));
    }

    // --------- REST JSON ---------

    private Object[] injectParameters(Method method, HttpServletRequest req, Map<String, String> pathParams) {
        Object[] params = new Object[method.getParameterCount()];

        for (int i = 0; i < method.getParameterCount(); i++) {

            // Parameter metadata
            java.lang.reflect.Parameter parameter = method.getParameters()[i];
            Class<?> paramType = parameter.getType();
            String paramName = parameter.getName(); // fallback if no annotation

            // ORDER 1: Check @RequestParam annotation
            core.annotation.RequestParam rp = parameter.getAnnotation(core.annotation.RequestParam.class);
            String key = (rp != null) ? rp.value() : paramName;

            String rawValue = null;

            // ORDER 2: FIRST try URL {variables}
            if (pathParams != null && pathParams.containsKey(key)) {
                rawValue = pathParams.get(key);
            }

            // ORDER 3: If not found, try query string / form
            if (rawValue == null) {
                rawValue = req.getParameter(key);
            }

            // ORDER 4: If still null → you decide (set null or throw error)
            // For now: allow null

            // Option 2:
            // throw new RuntimeException("Missing required parameter: " + key);

            // Convert to correct type
            Object converted = null;

            if (paramType == String.class) {
                converted = rawValue;
            } else if ((paramType == int.class || paramType == Integer.class)) {
                converted = (rawValue != null) ? Integer.parseInt(rawValue) : 0;
            } else if ((paramType == boolean.class || paramType == Boolean.class)) {
                converted = (rawValue != null) ? Boolean.parseBoolean(rawValue) : false;
            } else {
                converted = rawValue; // fallback default
            }

            params[i] = converted;
        }

        return params;
    }

}

// test d'un controller
// @Override
// public void init() {
// System.out.println(" Router initialized");
// try {
// Class<?> controllerClass = Class.forName("app.controllers.TestController");
// Object controller = controllerClass.getDeclaredConstructor().newInstance();
// registerController(controller);
// } catch (Exception e) {
// e.printStackTrace();
// }
// }
