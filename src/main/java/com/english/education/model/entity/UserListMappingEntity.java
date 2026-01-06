package com.english.education.model.entity;

import com.english.education.model.dto.response.user.UserListProjection;
import jakarta.persistence.*;

@Entity
@SqlResultSetMapping(
        name = "UserListProjectionMapping",
        classes = @ConstructorResult(
                targetClass = UserListProjection.class,
                columns = {
                        @ColumnResult(name = "id", type = Integer.class),
                        @ColumnResult(name = "username"),
                        @ColumnResult(name = "fullName"),
                        @ColumnResult(name = "email"),
                        @ColumnResult(name = "phone"),
                        @ColumnResult(name = "avatar"),
                        @ColumnResult(name = "status"),
                        @ColumnResult(name = "roles")
                }
        )
)
public class UserListMappingEntity {
    @Id
    private Integer id;
}
