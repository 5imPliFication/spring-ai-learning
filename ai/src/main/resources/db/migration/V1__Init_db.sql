
CREATE TABLE chats (
                       id VARCHAR(36) PRIMARY KEY, -- conversation_id (UUID)
                       user_id VARCHAR(100) NOT NULL,
                       title VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE spring_ai_chat_memory (
                                       conversation_id VARCHAR(36) NOT NULL,
                                       content TEXT NOT NULL,
                                       type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
                                       timestamp TIMESTAMP NOT NULL,
                                       sequence_id BIGINT NOT NULL
);

CREATE INDEX idx_chat_memory_conv_seq ON spring_ai_chat_memory(conversation_id, sequence_id);