package mx.edu.utez.sij.core.auth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import mx.edu.utez.sij.core.config.ConexionMySQL;
import mx.edu.utez.sij.core.util.JsonResponseUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String identificador = req.getParameter("usuario"); // Acepta clave_empleado o correo
        String password = req.getParameter("password");

        if (identificador == null || password == null || identificador.isBlank() || password.isBlank()) {
            JsonResponseUtil.sendMessage(resp, 400, false, "Ingrese su clave/correo y contraseña.");
            return;
        }

        String sql = "SELECT id_usuario, clave_empleado, nombre_completo, correo, password, rol, id_juzgado, "
                + "intentos_fallidos, bloqueado_hasta, activo FROM usuarios "
                + "WHERE (clave_empleado = ? OR correo = ?) LIMIT 1";

        try (Connection conn = ConexionMySQL.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, identificador.trim());
            ps.setString(2, identificador.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonResponseUtil.sendMessage(resp, 401, false, "Credenciales inválidas.");
                    return;
                }

                if (rs.getInt("activo") == 0) {
                    JsonResponseUtil.sendMessage(resp, 403, false, "Esta cuenta se encuentra inactiva.");
                    return;
                }

                int idUsuario = rs.getInt("id_usuario");
                int intentosFallidos = rs.getInt("intentos_fallidos");
                Timestamp bloqueadoTs = rs.getTimestamp("bloqueado_hasta");
                LocalDateTime ahora = LocalDateTime.now();

                // 1. Verificar si está actualmente bloqueado (30 minutos)
                if (bloqueadoTs != null && bloqueadoTs.toLocalDateTime().isAfter(ahora)) {
                    long minRestantes = Duration.between(ahora, bloqueadoTs.toLocalDateTime()).toMinutes() + 1;
                    JsonResponseUtil.sendMessage(resp, 423, false,
                            "Cuenta bloqueada por seguridad. Intente nuevamente en " + minRestantes + " minutos.");
                    return;
                }

                // 2. Validar contraseña (soporta texto plano de semilla o hash BCrypt)
                String storedPass = rs.getString("password");
                boolean passValida = storedPass.startsWith("$2a$")
                        ? BCrypt.checkpw(password, storedPass)
                        : storedPass.equals(password);

                if (!passValida) {
                    int nuevosIntentos = intentosFallidos + 1;
                    // Si llega a 3 intentos (o si ya venía de un bloqueo previo >= 3 y falló su único intento extra):
                    if (nuevosIntentos >= 3) {
                        String lockSql = "UPDATE usuarios SET intentos_fallidos = ?, bloqueado_hasta = DATE_ADD(NOW(), INTERVAL 30 MINUTE) WHERE id_usuario = ?";
                        try (PreparedStatement lockPs = conn.prepareStatement(lockSql)) {
                            lockPs.setInt(1, nuevosIntentos);
                            lockPs.setInt(2, idUsuario);
                            lockPs.executeUpdate();
                        }
                        JsonResponseUtil.sendMessage(resp, 423, false,
                                "Límite de intentos excedido. Su cuenta ha sido bloqueada durante 30 minutos.");
                    } else {
                        String failSql = "UPDATE usuarios SET intentos_fallidos = ? WHERE id_usuario = ?";
                        try (PreparedStatement failPs = conn.prepareStatement(failSql)) {
                            failPs.setInt(1, nuevosIntentos);
                            failPs.setInt(2, idUsuario);
                            failPs.executeUpdate();
                        }
                        int restantes = 3 - nuevosIntentos;
                        JsonResponseUtil.sendMessage(resp, 401, false,
                                "Contraseña incorrecta. Le quedan " + restantes + " intento(s) antes del bloqueo.");
                    }
                    return;
                }

                // 3. Login exitoso -> Reiniciar intentos y bloqueo
                try (PreparedStatement resetPs = conn.prepareStatement(
                        "UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?")) {
                    resetPs.setInt(1, idUsuario);
                    resetPs.executeUpdate();
                }

                String rol = rs.getString("rol");
                HttpSession session = req.getSession(true);
                session.setAttribute("idUsuario", idUsuario);
                session.setAttribute("claveEmpleado", rs.getString("clave_empleado"));
                session.setAttribute("nombreCompleto", rs.getString("nombre_completo"));
                session.setAttribute("rol", rol);
                session.setAttribute("idJuzgado", rs.getObject("id_juzgado"));

                String redirectUrl = rol.startsWith("ADMIN")
                        ? req.getContextPath() + "/views/admin/index.html"
                        : req.getContextPath() + "/views/dashboard/index.html";

                Map<String, Object> data = new HashMap<>();
                data.put("ok", true);
                data.put("rol", rol);
                data.put("nombreCompleto", rs.getString("nombre_completo"));
                data.put("claveEmpleado", rs.getString("clave_empleado"));
                data.put("redirectUrl", redirectUrl);
                JsonResponseUtil.sendJson(resp, 200, data);
            }
        } catch (SQLException e) {
            JsonResponseUtil.sendMessage(resp, 500, false, "Error interno de base de datos: " + e.getMessage());
        }
    }
}