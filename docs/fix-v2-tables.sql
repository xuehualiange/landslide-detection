USE landslide_db;

CREATE TABLE IF NOT EXISTS monitor_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  deformation_rate DECIMAL(10,4),
  temperature DECIMAL(6,2),
  seepage_pressure DECIMAL(10,4),
  geoelectric_field DECIMAL(10,4),
  collect_time DATETIME NOT NULL,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS landslide_detect_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  image_path VARCHAR(255) NOT NULL,
  landslide_area DECIMAL(12,4),
  max_confidence DECIMAL(6,4),
  disaster_level VARCHAR(32) NOT NULL,
  warning_triggered TINYINT NOT NULL DEFAULT 0,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_detect_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS warning_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message VARCHAR(500) NOT NULL,
  disaster_level VARCHAR(32) NOT NULL,
  landslide_area DECIMAL(12,4),
  max_confidence DECIMAL(10,6),
  latest_deformation_rate DECIMAL(10,4),
  status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
  ack_user_id BIGINT,
  ack_time DATETIME,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_warning_ack_user FOREIGN KEY (ack_user_id) REFERENCES sys_user(id)
);

CREATE INDEX idx_monitor_collect_time ON monitor_data(collect_time);
CREATE INDEX idx_detect_user_id ON landslide_detect_record(user_id);
CREATE INDEX idx_detect_created_time ON landslide_detect_record(created_time);
CREATE INDEX idx_warning_status ON warning_event(status);
CREATE INDEX idx_warning_created_time ON warning_event(created_time);

INSERT INTO monitor_data (deformation_rate, temperature, seepage_pressure, geoelectric_field, collect_time)
VALUES (0.15, 26.5, 0.32, 1.80, NOW());