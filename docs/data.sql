USE landslide_db;

-- 初始化角色
INSERT INTO sys_role (role_name, role_code)
SELECT '超级管理员', 'SUPER_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN');

INSERT INTO sys_role (role_name, role_code)
SELECT '管理员', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');

INSERT INTO sys_role (role_name, role_code)
SELECT '监测人员', 'MONITOR'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'MONITOR');

-- 初始化用户
-- 默认明文密码均为: 123456
-- BCrypt 哈希由工具生成，可直接用于 Spring Security BCryptPasswordEncoder 校验
INSERT INTO sys_user (username, password, real_name, phone, role_id, status)
SELECT 'superadmin', '$2b$12$d2TmXifLlP2IQ40F4pTIVuun8OcrPruyRum8QEm1iS5AhrMom7Jae', '系统超级管理员', '13800000001', r.id, 1
FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'superadmin');

INSERT INTO sys_user (username, password, real_name, phone, role_id, status)
SELECT 'admin', '$2b$12$iGq3VlGwRy14GDnEJXH3MusOiXc5zaVsqQm5QYtI4p.SZpP1teJ5q', '系统管理员', '13800000002', r.id, 1
FROM sys_role r
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user (username, password, real_name, phone, role_id, status)
SELECT 'monitor', '$2b$12$D8b8hV.RTeZU1gG3XDZJ5eMM9XPQlW66faEfPdFxEJ3eNdjTtfh0W', '监测员', '13800000003', r.id, 1
FROM sys_role r
WHERE r.role_code = 'MONITOR'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'monitor');
