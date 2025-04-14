package com.userrolemgmt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.userrolemgmt.dao.RoleDAO;
import com.userrolemgmt.model.Role;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CreateRoleFunction {
    private static final Logger LOGGER = Logger.getLogger(CreateRoleFunction.class.getName());
    private final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
    private final RoleDAO roleDAO = new RoleDAO();

    @FunctionName("createRole")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", 
                        methods = {HttpMethod.POST}, 
                        authLevel = AuthorizationLevel.ANONYMOUS,
                        route = "roles") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Solicitud recibida para crear un nuevo rol");
        
        String requestBody = request.getBody().orElse("");
        if (requestBody.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Por favor proporcione datos del rol en el cuerpo de la solicitud")
                    .build();
        }
        
        try {
            Role role = gson.fromJson(requestBody, Role.class);
            Role createdRole = roleDAO.createRole(role);
            
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(createdRole))
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al crear rol", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear rol: " + e.getMessage())
                    .build();
        }
    }
}