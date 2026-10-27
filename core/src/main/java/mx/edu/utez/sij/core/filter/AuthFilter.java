package mx.edu.utez.sij.core.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter("/views/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);
        boolean loggedIn = (session != null && session.getAttribute("idUsuario") != null);

        if (!loggedIn) {
            res.sendRedirect(req.getContextPath() + "/index.html");
            return;
        }
        chain.doFilter(request, response);
    }
}