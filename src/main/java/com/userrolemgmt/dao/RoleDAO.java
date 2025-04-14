package com.userrolemgmt.dao;

import com.userrolemgmt.model.Role;
import com.userrolemgmt.util.DatabaseConnection;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoleDAO {
    private static final Logger LOGGER = Logger.getLogger(RoleDAO.class.getName());
    private final Connection connection;

    public RoleDAO() {
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    // Crear un nuevo rol
    public Role createRole(Role role) throws SQLException {
        // Usar bloque PL/SQL con RETURNING INTO para Oracle
        String oracleQuery = "BEGIN " +
                "  INSERT INTO roles (role_name, description) " +
                "  VALUES (?, ?) " +
                "  RETURNING role_id, created_at, updated_at INTO ?, ?, ?; " +
                "END;";

        try (CallableStatement cstmt = connection.prepareCall(oracleQuery)) {
            // Parámetros de entrada
            cstmt.setString(1, role.getRoleName());
            cstmt.setString(2, role.getDescription());

            // Registrar parámetros de salida
            cstmt.registerOutParameter(3, Types.NUMERIC); // role_id
            cstmt.registerOutParameter(4, Types.TIMESTAMP); // created_at
            cstmt.registerOutParameter(5, Types.TIMESTAMP); // updated_at

            cstmt.execute();

            // Obtener valores devueltos
            role.setRoleId(cstmt.getLong(3));
            role.setCreatedAt(cstmt.getTimestamp(4));
            role.setUpdatedAt(cstmt.getTimestamp(5));

            return role;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al crear rol", e);
            throw e;
        }
    }
}