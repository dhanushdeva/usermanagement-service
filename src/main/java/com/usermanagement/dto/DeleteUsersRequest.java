package com.usermanagement.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "DeleteUsersRequest", description = " Containing user IDs and configuration details required to delete users")
public class DeleteUsersRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ArraySchema(schema = @Schema(description = "A user ID to be deleted"),
            arraySchema = @Schema(description = "List of user IDs to be deleted",
                    requiredMode = Schema.RequiredMode.REQUIRED))
    private List<String> userIds;
}
