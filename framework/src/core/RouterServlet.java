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

                routePatterns.add(new RoutePattern(path, method, controller));

                System.out.println("Registered route: " + path + " → " + method.getName());
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp);
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI().replace(req.getContextPath(), "");

        // Find a dynamic route match -- sprint 3 bbis
        RoutePattern matched = null;
        Map<String, String> params = null;

        // Look for a matching route
        for (RoutePattern rp : routePatterns) {
            params = rp.match(path);
            if (params != null) {
                matched = rp;
                break;
            }
        }

        if (matched == null) {
            resp.setStatus(404);
            resp.getWriter().write("404 - Not Found: " + path);
            return;
        }

        try {
            // Prepare to inject parameters
            Object result;

            // If the method has one parameter and it is a Map, inject the params into it
            if (matched.method.getParameterCount() == 1 && matched.method.getParameterTypes()[0] == Map.class) {
                result = matched.method.invoke(matched.controller, params);

            } else {
                // Inject individual parameters based on method signature
                Object[] methodArgs = injectParameters(matched.method, req);
                result = matched.method.invoke(matched.controller, methodArgs);
            }

            // Handle ModelView → JSP
            if (result instanceof ModelView mv) {
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }

                req.getRequestDispatcher("/WEB-INF/views/" + mv.getView()).forward(req, resp);
                return;
            }

            // Handle String output
            if (result instanceof String str) {
                resp.getWriter().write(str);
                return;
            }

            // Unsupported return type
            resp.getWriter().write("Unsupported return type from controller");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("500 - Server error: " + e.getMessage());
        }
    }

    private Object[] injectParameters(Method method, HttpServletRequest req) {
        Object[] params = new Object[method.getParameterCount()];

        for (int i = 0; i < method.getParameterCount(); i++) {
            // Get the parameter type
            Class<?> paramType = method.getParameterTypes()[i];

            // Handle different parameter types (String, Integer, etc.)
            String paramValue = null;
            String paramName = method.getParameters()[i].getName(); // Get the parameter name

            // Try to get the value from request parameters (query string or form data)
            paramValue = req.getParameter(paramName);

            if (paramValue == null) {
                // You may want to handle path parameters here if needed
                // Ex: extract from path if your framework supports that
            }

            // Convert to the correct type
            if (paramType == String.class) {
                params[i] = paramValue;
            } else if (paramType == Integer.class || paramType == int.class) {
                if (paramValue != null) {
                    params[i] = Integer.parseInt(paramValue);
                }
            } else if (paramType == Boolean.class || paramType == boolean.class) {
                if (paramValue != null) {
                    params[i] = Boolean.parseBoolean(paramValue);
                }
            } else {
                // Handle other types or fallback
                params[i] = paramValue; // Default to String if type not found
            }
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

// scanner tout les controller