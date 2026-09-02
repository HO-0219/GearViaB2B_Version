-- McpPersonalToken.tokenHash maps to a JPA String (length 64), which Hibernate
-- schema validation expects as VARCHAR(64). V5 created it as CHAR(64), which
-- fails `spring.jpa.hibernate.ddl-auto=validate` on MySQL. Align the column type.
ALTER TABLE mcp_personal_tokens MODIFY token_hash VARCHAR(64) NOT NULL;
