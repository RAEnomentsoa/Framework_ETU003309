package core;

import core.annotation.Controller;
import core.annotation.Route;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouterServlet extends HttpServlet {

    public final Map<String, Method> routes = new HashMap<>();
    public final Map<String, Object> controllers = new HashMap<>();

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

                    // Optional: check annotation like @Controller
                    if (clazz.getSimpleName().endsWith("Controller")) {
                        Object controller = clazz.getDeclaredConstructor().newInstance();
                        registerController(controller);
                        getServletContext().setAttribute("routes", routes);
                        getServletContext().setAttribute("controllers", controllers);

                        System.out.println("Controllers");
                        System.out.println("Registered: " + clazz.getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // register controller
    public void registerController(Object controller) {
        Class<?> clazz = controller.getClass();
        if (clazz.isAnnotationPresent(Controller.class)) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Route.class)) {
                    String path = method.getAnnotation(Route.class).value();
                    routes.put(path, method);
                    controllers.put(path, controller);
                    System.out.println("Registered route: " + path + " → " + method.getName());
                }
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
        Method method = routes.get(path);

        if (method == null) {
            resp.setStatus(404);
            resp.getWriter().write("404 - Not Found: " + path);
            return;
        }

        try {
            Object controller = controllers.get(path);
            String controllerName = controller.getClass().getSimpleName();
            String methodName = method.getName();

            Class<?> returnType = method.getReturnType();
            String returnTypeName = returnType.getSimpleName();

            Object result = method.getParameterCount() == 4
                    ? method.invoke(controller, path, methodName, controllerName,
                            method.getReturnType().getSimpleName())
                    : method.getParameterCount() == 3 ? method.invoke(controller, path, methodName, controllerName)
                            : method.getParameterCount() == 2 ? method.invoke(controller, path, method.getName())
                                    : method.invoke(controller);

            // detect Modelview
            // CASE 1: Controller returns ModelView → JSP
            if (result instanceof ModelView mv) {

                // send attributes to JSP
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }

                // forward to JSP
                req.getRequestDispatcher("/WEB-INF/views/" + mv.getView()).forward(req, resp);
                return;
            }

            // CASE 2: Controller returns String → print normally
            if (result instanceof String str) {
                resp.getWriter().write(str);
                return;
            }

            // CASE 3: Unsupported return
            resp.getWriter().write("Unsupported return type from controller");

            // show type + value on response
            resp.getWriter().write("controller:" + controller.getClass().getSimpleName() + "\n");
            resp.getWriter().write("method: " + method.getName() + "\n");
            resp.getWriter().write("Return type: " + returnType.getSimpleName() + "\n");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("500 - Server error: " + e.getMessage());
        }
    }
}
