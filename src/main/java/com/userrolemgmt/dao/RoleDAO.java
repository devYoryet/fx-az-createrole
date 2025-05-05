package com.userrolemgmt.dao;

import com.userrolemgmt.model.Role;
import com.userrolemgmt.model.User;
import com.userrolemgmt.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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

    public boolean deleteRole(long roleId) throws SQLException {
        // Primero obtener información sobre el rol para registro y auditoría
        Role roleToDelete = getRoleById(roleId);
        if (roleToDelete == null) {
            LOGGER.log(Level.WARNING, "Intento de eliminar rol inexistente ID: " + roleId);
            return false;
        }

        LOGGER.log(Level.INFO, "Eliminando rol: " + roleToDelete.getRoleName() + " (ID: " + roleId + ")");

        // Obtener los usuarios afectados antes de eliminar las relaciones
        List<User> affectedUsers = getUsersByRoleId(roleId);
        LOGGER.log(Level.INFO, "Se verán afectados " + affectedUsers.size() + " usuarios por la eliminación del rol");

        // Eliminar todas las referencias en user_roles
        String deleteUserRolesQuery = "DELETE FROM user_roles WHERE role_id = ?";
        int rowsDeleted = 0;

        try (PreparedStatement pstmt = connection.prepareStatement(deleteUserRolesQuery)) {
            pstmt.setLong(1, roleId);
            rowsDeleted = pstmt.executeUpdate();
            LOGGER.log(Level.INFO, "Se eliminaron " + rowsDeleted + " asignaciones de rol a usuarios");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al eliminar relaciones de user_roles para rol ID: " + roleId, e);
            throw e;
        }

        // Luego eliminar el rol
        String deleteRoleQuery = "DELETE FROM roles WHERE role_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteRoleQuery)) {
            pstmt.setLong(1, roleId);
            int rowsAffected = pstmt.executeUpdate();

            // Registra información sobre la operación
            if (rowsAffected > 0) {
                LOGGER.log(Level.INFO,
                        "Rol eliminado correctamente: " + roleToDelete.getRoleName() + " (ID: " + roleId + ")");

                // Registra información sobre los usuarios afectados
                if (!affectedUsers.isEmpty()) {
                    StringBuilder userInfo = new StringBuilder();
                    for (User user : affectedUsers) {
                        userInfo.append(user.getUsername()).append(" (ID: ").append(user.getUserId()).append("), ");
                    }
                    LOGGER.log(Level.INFO, "Usuarios afectados por la eliminación del rol: " + userInfo);
                }
            }

            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al eliminar rol ID: " + roleId, e);
            throw e;
        }
    }

    /**
     * Obtiene todos los usuarios asignados a un rol específico
     * 
     * @param roleId ID del rol
     * @return Lista de usuarios asignados al rol
     * @throws SQLException si ocurre un error de base de datos
     */
    public List<User> getUsersByRoleId(long roleId) throws SQLException {
        List<User> users = new ArrayList<>();
        String query = "SELECT u.* FROM users u " +
                "JOIN user_roles ur ON u.user_id = ur.user_id " +
                "WHERE ur.role_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setLong(1, roleId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Manejar campo active que es un CHAR(1) 'Y'/'N'
                    String activeString = rs.getString("active");
                    boolean isActive = "Y".equalsIgnoreCase(activeString);

                    User user = new User(
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            isActive,
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at"));
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al obtener usuarios para rol ID: " + roleId, e);
            throw e;
        }

        return users;
    }

    /**
     * Obtiene un rol por su ID
     * 
     * @param roleId ID del rol a buscar
     * @return El objeto Role si se encuentra, null si no existe
     * @throws SQLException si ocurre un error de base de datos
     */
    public Role getRoleById(long roleId) throws SQLException {
        String query = "SELECT * FROM roles WHERE role_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setLong(1, roleId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRole(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al obtener rol por ID: " + roleId, e);
            throw e;
        }

        return null;
    }

    /**
     * Método auxiliar para mapear un ResultSet a un objeto Role
     */
    private Role mapResultSetToRole(ResultSet rs) throws SQLException {
        return new Role(
                rs.getLong("role_id"),
                rs.getString("role_name"),
                rs.getString("description"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at"));
    }
}