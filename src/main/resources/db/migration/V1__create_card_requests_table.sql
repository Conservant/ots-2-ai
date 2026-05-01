CREATE TABLE card_requests (
    id         UUID         NOT NULL,
    request_id UUID         NOT NULL,
    card_id    VARCHAR(255) NOT NULL,
    client_id  VARCHAR(255) NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_card_requests PRIMARY KEY (id)
);
