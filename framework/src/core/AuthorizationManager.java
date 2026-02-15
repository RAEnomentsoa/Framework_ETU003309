package core;

import core.Session;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthorizationManager {

    public static void setAuthenticatedUser(Session session, String username, String role) {
        session.set("user", username);
        session.set("role", role);
    }

    public static void logout(Session session) {
        session.invalidate();
    }

    public static String getCurrentUser(Session session) {
        return (String) session.get("user");
    }

    public static String getCurrentUserRole(Session session) {
        return (String) session.get("role");
    }

    public static boolean checkAccess(Session session, String[] allowedRoles) {
        String role = getCurrentUserRole(session);
        if(role == null) role = "anonym";

        for(String r : allowedRoles){
            if(r.equalsIgnoreCase("all")) return true;
            if(r.equalsIgnoreCase("anonym") && role.equals("anonym")) return true;
            if(role.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    public static boolean enforce(Session session, String[] allowedRoles, HttpServletResponse resp) throws IOException {
        if(!checkAccess(session, allowedRoles)) {
            if(getCurrentUser(session) == null){
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Login required");
            } else {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Access denied");
            }
            return false;
        }
        return true;
    }
}
