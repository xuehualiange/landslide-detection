CREATE DATABASE IF NOT EXISTS landslide_db DEFAULT CHARACTER SET utf8mb4;
USE landslide_db;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭ID',
  role_name VARCHAR(64) NOT NULL COMMENT '瑙掕壊鍚嶇О',
  role_code VARCHAR(32) NOT NULL UNIQUE COMMENT '瑙掕壊缂栫爜: SUPER_ADMIN/ADMIN/MONITOR',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿'
) COMMENT='瑙掕壊琛?;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭ID',
  username VARCHAR(64) NOT NULL UNIQUE COMMENT '鐢ㄦ埛鍚?,
  password VARCHAR(128) NOT NULL COMMENT '瀵嗙爜(寤鸿瀛樺偍鍔犲瘑鍚庣殑鍝堝笇)',
  real_name VARCHAR(64) NOT NULL COMMENT '鐪熷疄濮撳悕',
  phone VARCHAR(20) COMMENT '鑱旂郴鐢佃瘽',
  role_id BIGINT NOT NULL COMMENT '瑙掕壊ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '鐘舵€?1鍚敤,0绂佺敤',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) COMMENT='鐢ㄦ埛琛?;

CREATE TABLE IF NOT EXISTS landslide_detect_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭ID',
  user_id BIGINT NOT NULL COMMENT '涓婁紶鐢ㄦ埛ID',
  image_path VARCHAR(255) NOT NULL COMMENT '鍥剧墖瀛樺偍璺緞',
  landslide_area DECIMAL(12,4) COMMENT '婊戝潯闈㈢Н(鍗曚綅鍙寜涓氬姟绾﹀畾)',
  max_confidence DECIMAL(6,4) COMMENT '鏈€澶х疆淇″害',
  disaster_level VARCHAR(32) NOT NULL COMMENT '鐏炬儏绛夌骇:LOW/MEDIUM/HIGH鎴栬交搴?涓害/閲嶅害',
  warning_triggered TINYINT NOT NULL DEFAULT 0 COMMENT '鏄惁瑙﹀彂棰勮:1鏄?0鍚?,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  CONSTRAINT fk_detect_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) COMMENT='婊戝潯璇嗗埆璁板綍琛?;

CREATE TABLE IF NOT EXISTS monitor_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭ID',
  deformation_rate DECIMAL(10,4) COMMENT '鍙樺舰閫熺巼',
  temperature DECIMAL(6,2) COMMENT '娓╁害',
  seepage_pressure DECIMAL(10,4) COMMENT '娓楁祦鍘嬪姏',
  geoelectric_field DECIMAL(10,4) COMMENT '鍦扮數鍦?,
  collect_time DATETIME NOT NULL COMMENT '閲囬泦鏃堕棿',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿'
) COMMENT='鐩戞祴鏁版嵁琛?;

CREATE INDEX idx_user_role_id ON sys_user(role_id);
CREATE INDEX idx_detect_user_id ON landslide_detect_record(user_id);
CREATE INDEX idx_detect_created_time ON landslide_detect_record(created_time);
CREATE INDEX idx_monitor_collect_time ON monitor_data(collect_time);

CREATE TABLE IF NOT EXISTS warning_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '预警事件ID',
  message VARCHAR(500) NOT NULL COMMENT '预警消息',
  disaster_level VARCHAR(32) NOT NULL COMMENT '灾情等级',
  landslide_area DECIMAL(12,4) COMMENT '滑坡面积',
  max_confidence DECIMAL(10,6) COMMENT '最大置信度',
  latest_deformation_rate DECIMAL(10,4) COMMENT '最新变形速率',
  status VARCHAR(16) NOT NULL DEFAULT 'UNREAD' COMMENT '状态: UNREAD/ACK',
  ack_user_id BIGINT COMMENT '确认人用户ID',
  ack_time DATETIME COMMENT '确认时间',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CONSTRAINT fk_warning_ack_user FOREIGN KEY (ack_user_id) REFERENCES sys_user(id)
) COMMENT='实时预警事件表';

CREATE INDEX idx_warning_status ON warning_event(status);
CREATE INDEX idx_warning_created_time ON warning_event(created_time);