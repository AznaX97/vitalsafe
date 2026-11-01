CREATE TABLE vital_readings (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL,
    unit            VARCHAR(16)  NOT NULL,
    measured_at     TIMESTAMPTZ  NOT NULL,
    value_primary   TEXT         NOT NULL,   -- encrypted
    value_secondary TEXT,                    -- encrypted (diastolic pressure)
    note            TEXT,                    -- encrypted
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT vital_readings_type_valid CHECK (type IN (
        'BLOOD_PRESSURE', 'HEART_RATE', 'BODY_WEIGHT',
        'BODY_TEMPERATURE', 'OXYGEN_SATURATION', 'BLOOD_GLUCOSE'))
);

CREATE INDEX idx_vital_readings_user_time      ON vital_readings (user_id, measured_at DESC);
CREATE INDEX idx_vital_readings_user_type_time ON vital_readings (user_id, type, measured_at DESC);