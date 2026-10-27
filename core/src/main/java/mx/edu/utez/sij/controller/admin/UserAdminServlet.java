package mx.edu.utez.sij.controller.admin;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.edu.utez.sij.core.config.ConexionMySQL;
import mx.edu.utez.sij.core.util.EmployeeCodeUtil;
import mx.edu.utez.sij.core.util.JsonResponseUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/admin/users")
public class UserAdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sql = "SELECT u.id_usuario, u.clave_empleado, u.nombre_completo, u.correo, u.rol, u.activo, "
                + "j.numero_juzgado, j.nombre_juzgado "
                + "FROM usuarios u LEFT JOIN juzgados j ON u.id_juzgado = j.id_juzgado ORDER BY u.id_usuario DESC";

        List<Map<String, Object>> usuarios = new ArrayList<>();
        try (Connection conn = ConexionMySQL.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("idUsuario", rs.getInt("id_usuario"));
                row.put("claveEmpleado", rs.getString("clave_empleado"));
                row.put("nombreCompleto", rs.getString("nombre_completo"));
                row.put("correo", rs.getString("correo"));
                row.put("rol", rs.getString("rol"));
                row.put("juzgado", rs.getString("numero_juzgado") != null
                        ? rs.getString("numero_juzgado") + " - " + rs.getString("nombre_juzgado")
                        : "Sin asignar");
                row.put("activo", rs.getInt("activo") == 1);
                usuarios.add(row);
            }
            JsonResponseUtil.sendJson(resp, 200, usuarios);
        } catch (SQLException e) {
            JsonResponseUtil.sendMessage(resp, 500, false, "Error al listar usuarios: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accion = req.getParameter("accion");

        try (Connection conn = ConexionMySQL.getConnection()) {
            if ("toggle".equalsIgnoreCase(accion)) {
                int idUsuario = Integer.parseInt(req.getParameter("idUsuario"));
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE usuarios SET activo = IF(activo = 1, 0, 1), intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?")) {
                    ps.setInt(1, idUsuario);
                    ps.executeUpdate();
                }
                JsonResponseUtil.sendMessage(resp, 200, true, "Estatus del usuario actualizado.");
                return;
            }

            // Alta de nuevo servidor público (los 4 datos esenciales)
            String nombreCompleto = req.getParameter("nombreCompleto");
            String correo = req.getParameter("correo");
            String password = req.getParameter("password");
            String rol = req.getParameter("rol");
            int idJuzgado = Integer.parseInt(req.getParameter("idJuzgado"));

            int siguienteId = 1;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id_usuario), 0) + 1 FROM usuarios")) {
                if (rs.next()) siguienteId = rs.getInt(1);
            }

            String claveGenerada = EmployeeCodeUtil.generateEmployeeCode(rol, nombreCompleto, siguienteId);
            String hashedPass = BCrypt.hashpw(password, BCrypt.gensalt());

            String insertSql = "INSERT INTO usuarios (clave_empleado, nombre_completo, correo, password, rol, id_juzgado) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, claveGenerada);
                ps.setString(2, nombreCompleto.trim());
                ps.setString(3, correo.trim());
                ps.setString(4, hashedPass);
                ps.setString(5, rol);
                ps.setInt(6, idJuzgado);
                ps.executeUpdate();
            }

            JsonResponseUtil.sendMessage(resp, 201, true, "Empleado registrado con clave: " + claveGenerada);
        } catch (SQLException e) {
            JsonResponseUtil.sendMessage(resp, 500, false, "Error al guardar usuario (verifique que el correo no esté repetido): " + e.getMessage());
        }
    }
}