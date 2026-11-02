CREATE TABLE target_ranges (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(30)  NOT NULL,
    unit           VARCHAR(16)  NOT NULL,
    low_primary    TEXT,        -- encrypted
    high_primary   TEXT,        -- encrypted
    low_secondary  TEXT,        -- encrypted (diastolic pressure)
    high_secondary TEXT,        -- encrypted (diastolic pressure)
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT target_ranges_type_valid CHECK (type IN (
        'BLOOD_PRESSURE', 'HEART_RATE', 'BODY_WEIGHT',
        'BODY_TEMPERATURE', 'OXYGEN_SATURATION', 'BLOOD_GLUCOSE')),
    CONSTRAINT target_ranges_one_per_type_unit UNIQUE (user_id, type, unit),
    CONSTRAINT target_ranges_has_a_bound CHECK (
        low_primary IS NOT NULL OR high_primary IS NOT NULL
        OR low_secondary IS NOT NULL OR high_secondary IS NOT NULL)
);