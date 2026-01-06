package com.english.education.constant.query.user;

public class UserQuery {

    private UserQuery() {
        // prevent instantiation
    }

    public static final String GET_ALL_USER = """
                  SELECT
                    u.id,
                    u.username,
                    u.full_name AS fullName,
                    u.email,
                    u.phone,
                    u.avatar,
                    u.status,
                    GROUP_CONCAT(ur.role ORDER BY ur.role) AS roles
                FROM users u
                LEFT JOIN user_role ur ON u.id = ur.user_id
                WHERE u.deleted_at IS NULL
            """;

    public static final String COUNT_GET_ALL_USER = """
                            SELECT COUNT(DISTINCT u.id)
                            FROM users u
                            LEFT JOIN user_role ur ON u.id = ur.user_id
                            WHERE u.deleted_at IS NULL
                        """;
}
