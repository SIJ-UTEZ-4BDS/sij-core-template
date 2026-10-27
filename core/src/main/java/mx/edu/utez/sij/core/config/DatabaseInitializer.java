package mx.edu.utez.sij.core.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

@WebListener
public class DatabaseInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[SIJ-Core] Verificando e inicializando esquema de Base de Datos en MySQL...");
        try (Connection conn = ConexionMySQL.getConnection();
             Statement stmt = conn.createStatement();
             InputStream is = getClass().getClassLoader().getResourceAsStream("sql/schema.sql")) {

            if (is == null) {
                System.err.println("[SIJ-Core] No se encontro sql/schema.sql en resources.");
                return;
            }

            String sqlScript = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));

            for (String command : sqlScript.split(";")) {
                if (!command.trim().isEmpty()) {
                    stmt.execute(command.trim());
                }
            }

            System.out.println("[SIJ-Core] Esquema base y catalogos inicializados correctamente.");
            injectBootstrapAdminIfEmpty(conn);
        } catch (Exception e) {
            System.err.println("[SIJ-Core] Error al inicializar la Base de Datos: " + e.getMessage());
        }
    }

    private void injectBootstrapAdminIfEmpty(Connection conn) {
        String checkSql = "SELECT COUNT(*) FROM usuarios";
        try (Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                java.util.Properties props = new java.util.Properties();
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("db.properties")) {
                    if (is != null) props.load(is);
                }
                String clave = props.getProperty("app.bootstrap.clave", "ROOT-SIJ-0001");
                String nombre = props.getProperty("app.bootstrap.nombre", "Administrador General");
                String correo = props.getProperty("app.bootstrap.correo", "root@sij.gob.mx");
                String rawPass = props.getProperty("app.bootstrap.password", "ChangeMe123!");
                String rol = props.getProperty("app.bootstrap.rol", "ADMIN_OFICIALIA");

                String hashedPass = org.mindrot.jbcrypt.BCrypt.hashpw(rawPass, org.mindrot.jbcrypt.BCrypt.gensalt());

                String insertSql = "INSERT INTO usuarios (clave_empleado, nombre_completo, correo, password, rol, id_juzgado) VALUES (?, ?, ?, ?, ?, 1)";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, clave);
                    ps.setString(2, nombre);
                    ps.setString(3, correo);
                    ps.setString(4, hashedPass);
                    ps.setString(5, rol);
                    ps.executeUpdate();
                    System.out.println("[SIJ-Core] Usuario ROOT inyectado desde db.properties con hash BCrypt.");
                }
            }
        } catch (Exception e) {
            System.err.println("[SIJ-Core] Error al inyectar Bootstrap Admin: " + e.getMessage());
        }
    }
}
