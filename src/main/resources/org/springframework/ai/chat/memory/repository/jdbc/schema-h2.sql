-- Spring AI 1.0 Chat Memory Repository — H2 schema
-- 위치: classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql
-- (Spring AI 1.0 starter가 이 정확한 경로에서 schema 찾음. H2는 기본 미제공이라 직접 작성 필요.)
-- QUEST 3단계 함정 #3 — Spring AI 1.0 GA의 spring-ai-starter-model-chat-memory-repository-jdbc는
-- PostgreSQL·MySQL·MariaDB schema는 제공하지만 H2는 미포함.

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content         CLOB        NOT NULL,
    type            VARCHAR(10) NOT NULL,
    "timestamp"     TIMESTAMP   NOT NULL,
    CONSTRAINT TYPE_CHECK CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");
