CREATE TABLE IF NOT EXISTS pending_import (
    id TEXT PRIMARY KEY,
    file_name TEXT NOT NULL,
    status TEXT NOT NULL,
    error_message TEXT,
    supplier_name TEXT,
    commercial_document_number TEXT,
    warehouse_document_number TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_import_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    import_id TEXT NOT NULL,
    row_order INTEGER NOT NULL,
    barcode TEXT,
    name TEXT NOT NULL,
    expected_qty INTEGER,
    unit TEXT NOT NULL DEFAULT 'szt',
    FOREIGN KEY (import_id) REFERENCES pending_import (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS delivery (
    id TEXT PRIMARY KEY,
    source_file_name TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    activated_at TEXT,
    supplier_name TEXT,
    commercial_document_number TEXT,
    warehouse_document_number TEXT
);

CREATE TABLE IF NOT EXISTS delivery_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id TEXT NOT NULL,
    barcode TEXT NOT NULL,
    name TEXT NOT NULL,
    expected_qty INTEGER NOT NULL,
    unit TEXT NOT NULL DEFAULT 'szt',
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

-- Separate from device_scan: posting a comment never changes counted quantities.
-- schema.sql runs on every startup, including installations with an existing DB.
CREATE TABLE IF NOT EXISTS item_comment (
    comment_id TEXT PRIMARY KEY,
    delivery_id TEXT NOT NULL,
    barcode TEXT NOT NULL,
    original_barcode TEXT NOT NULL,
    original_name TEXT NOT NULL,
    device_id TEXT NOT NULL,
    device_name TEXT,
    comment_text TEXT NOT NULL,
    suggested_barcode TEXT,
    suggested_name TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (delivery_id) REFERENCES delivery (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_item_comment_product ON item_comment(delivery_id, barcode, created_at);
