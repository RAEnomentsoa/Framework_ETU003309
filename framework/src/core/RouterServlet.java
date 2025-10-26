package core;

import core.annotation.Controller;
import core.annotation.Route;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouterServlet extends HttpServlet {

    public final Map<String, Method> routes = new HashMap<>();
    public final Map<String, Object> controllers = new HashMap<>();

    @Override 
    public void init() {
        System.out.println(" Router initialized");
        try {
            Class<?> controllerClass = Class.forName("app.controllers.TestController");
            Object controller = controllerClass.getDeclaredConstructor().newInstance();
            registerController(controller);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
              // Alernative: afaka manao fonction scan ny package iray manontolo ho an'ny controllers
               // sy ny routes ao anatiny mba hiregister-ena azy ireo rehetra
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
            String result;
            if (method.getParameterCount() == 2) {
                result = (String) method.invoke(controller, path, method.getName());
            } else {
                result = (String) method.invoke(controller);
            }
            resp.getWriter().write(result);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("500 - Server error: " + e.getMessage());
        }
    }
}
