package com.english.education.model.repository.user;

import com.english.education.constant.MessageConstant;
import com.english.education.constant.query.user.UserQuery;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.response.user.UserListProjection;
import com.english.education.model.enums.RoleName;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private static final Map<String, String> SORT_FIELD_MAP = Map.of(
            "id", "u.id",
            "username", "u.username",
            "email", "u.email",
            "phone", "u.phone"
    );

    private final EntityManager em;

    /**
     * Search and paginate users with optional filters and dynamic sorting.
     * <p>
     * Supported filters:
     * - search: fuzzy search on username, full name, email, phone
     * - status: filter by user status
     * - roles: filter users having at least one of the given roles
     * <p>
     * Query strategy:
     * - Uses native SQL for better control over joins, grouping and performance
     * - Builds data query and count query separately
     * - Ensures both queries always share the same WHERE conditions
     * <p>
     * Pagination:
     * - Offset and limit are applied only to the data query
     * - Count query returns total number of distinct users
     * <p>
     * Sorting:
     * - Sorting is applied dynamically based on Pageable
     * - Only whitelisted fields are allowed (validated in buildOrderBy)
     * - Prevents SQL injection via sort field
     * <p>
     * Grouping:
     * - GROUP BY u.id is required to avoid duplicated users
     *   when joining with user_role table
     * <p>
     * Error handling:
     * - Throws CustomException if an invalid sort field is provided
     *
     * @param search   optional keyword for fuzzy search
     * @param status   optional user status filter
     * @param roles    optional role filter (IN condition)
     * @param pageable pagination and sorting information
     * @return paginated list of users with total count
     * @throws CustomException if sort field is invalid
     * @author Duc Hai (07/01/2026)
     */
    @Override
    public Page<UserListProjection> searchAll(String search, String status, Set<RoleName> roles, Pageable pageable) throws CustomException {

        // Base SELECT query (data)
        StringBuilder dataSql = new StringBuilder(UserQuery.GET_ALL_USER);

        // Base COUNT query (total records)
        StringBuilder countSql = new StringBuilder(UserQuery.COUNT_GET_ALL_USER);

        // Parameters shared between data query and count query
        Map<String, Object> params = new HashMap<>();

        // Apply fuzzy search filter
        if(search != null && !search.isEmpty()) {

            String searchCondition = """
                    AND (
                        u.username LIKE :search
                        OR u.full_name LIKE :search
                        OR u.email LIKE :search
                        OR u.phone LIKE :search
                    )
                    """;
            dataSql.append(searchCondition);
            countSql.append(searchCondition);
            params.put("search", search);
        }

        // Apply status filter
        if(status != null && !status.isEmpty()) {
            String statusCondition = """
                    AND u.status = :status
                    """;
            dataSql.append(statusCondition);
            countSql.append(statusCondition);
            params.put("status", status);
        }

        // Apply role filter
        // Note: filters users having at least one role in the provided set
        if(roles != null && !roles.isEmpty()) {
            List<String> roleNames = roles.stream()
                    .map(Enum::name)
                    .toList();

            String roleCondition = """
                    AND ur.role IN (:roles)
                    """;
            dataSql.append(roleCondition);
            countSql.append(roleCondition);

            params.put("roles", roleNames);
        }

        // Group by user ID to avoid duplicate rows caused by LEFT JOIN user_role
        dataSql.append(" GROUP BY u.id");

        // Apply sorting (validated and whitelisted)
        dataSql.append(buildOrderBy(pageable.getSort()));

        // Create native data query with result mapping
        Query dataQuery = em.createNativeQuery(
                dataSql.toString(),
                "UserListProjectionMapping"
        );

        // Create native count query
        Query countQuery = em.createNativeQuery(countSql.toString());

        // Bind all parameters to both queries
        params.forEach((k, v) -> {
            dataQuery.setParameter(k, v);
            countQuery.setParameter(k, v);
        });

        // Apply pagination only to data query
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<UserListProjection> data = dataQuery.getResultList();

        // Total number of distinct users
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(data, pageable, total);

    }

    /**
     * Build ORDER BY clause based on Pageable sorting.
     * <p>
     * Security considerations:
     * - Only allows sorting by predefined, whitelisted fields
     * - Prevents SQL injection via dynamic ORDER BY
     * <p>
     * Default behavior:
     * - If no sorting is provided, order by u.id ASC
     *
     * @param sort Spring Data Sort object
     * @return SQL ORDER BY clause
     * @throws CustomException if sort field is not allowed
     * @author Duc Hai (07/01/2026)
     */
    private String buildOrderBy(Sort sort) throws CustomException {

        // Default sorting when no sort is specified
        if (!sort.isSorted()) {
            return " ORDER BY u.id ASC";
        }

        Sort.Order order = sort.iterator().next();

        // Map API sort field to actual database column
        String column = SORT_FIELD_MAP.get(order.getProperty());

        // Reject invalid or non-whitelisted sort fields
        if (column == null) {
            throw new CustomException(MessageConstant.INVALID_SORT_FIELD, HttpStatus.BAD_REQUEST);
        }

        return " ORDER BY " + column + " " + order.getDirection().name();
    }
}
