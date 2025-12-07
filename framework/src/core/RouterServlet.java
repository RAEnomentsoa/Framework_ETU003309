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
            // Invoke controller
            Object result;

            if (matched.method.getParameterCount() == 1 &&
                    matched.method.getParameterTypes()[0] == Map.class) {

                result = matched.method.invoke(matched.controller, params);

            } else {
                result = matched.method.invoke(matched.controller);
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

            // Unsupported return
            resp.getWriter().write("Unsupported return type from controller");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("500 - Server error: " + e.getMessage());
        }
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