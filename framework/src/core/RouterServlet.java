package core;

import core.annotation.Controller;
import core.annotation.Route;
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
        if (matchedByPath == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("404 - Not Found: " + path);
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
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            resp.setHeader("Allow", String.join(", ", allowedMethods));
            resp.getWriter().write("405 - Method " + requestMethod + " Not Allowed on " + path);
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
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("500 - Server error: " + e.getMessage());
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

            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            String rawValue = req.getParameter(fieldName);

            // Checkbox non cochée -> null (donc laisse false par défaut)
            if (rawValue == null) {
                continue;
            }

            Object converted;

            if (fieldType == String.class) {
                converted = rawValue;
            } else if (fieldType == int.class || fieldType == Integer.class) {
                converted = Integer.parseInt(rawValue);
            } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                // HTML checkbox renvoie souvent "on"
                converted = rawValue.equalsIgnoreCase("true") || rawValue.equalsIgnoreCase("on");
            } else if (fieldType == double.class || fieldType == Double.class) {
                converted = Double.parseDouble(rawValue);
            } else if (fieldType == float.class || fieldType == Float.class) {
                converted = Float.parseFloat(rawValue);
            } else if (fieldType == long.class || fieldType == Long.class) {
                converted = Long.parseLong(rawValue);
            } else {
                // fallback: garder en String
                converted = rawValue;
            }

            field.set(obj, converted);
        }

        return obj;
    }

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
