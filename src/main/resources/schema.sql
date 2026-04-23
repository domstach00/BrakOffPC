CREATE TABLE IF NOT EXISTS pending_import (
    id TEXT PRIMARY KEY,
    file_name TEXT NOT NULL,
    status TEXT NOT NULL,
    error_message TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_import_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    import_id TEXT NOT NULL,
    row_order INTEGER NOT NULL,
    barcode TEXT,
    name TEXT NOT NULL,
    expected_qty INTEGER,
    FOREIGN KEY (import_id) REFERENCES pending_import (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS delivery (
    id TEXT PRIMARY KEY,
    source_file_name TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    activated_at TEXT
);

CREATE TABLE IF NOT EXISTS delivery_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id TEXT NOT NULL,
    barcode TEXT NOT NULL,
    name TEXT NOT NULL,
    expected_qty INTEGER NOT NULL,
    FOREIGN KEY (delivery_id) REFERENCES delivery (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_scan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    device_name TEXT,
    barcode TEXT NOT NULL,
    item_name TEXT,
    quantity INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(delivery_id, device_id, barcode),
    FOREIGN KEY (delivery_id) REFERENCES delivery (id) ON DELETE CASCADE
);
