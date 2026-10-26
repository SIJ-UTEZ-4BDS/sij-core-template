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
        } catch (Exception e) {
            System.err.println("[SIJ-Core] Error al inicializar la Base de Datos: " + e.getMessage());
        }
    }
}
