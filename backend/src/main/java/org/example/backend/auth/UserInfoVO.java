package org.example.backend.auth;

import org.example.backend.system.entity.SysPermission;

import java.util.List;
import java.util.Set;

public class UserInfoVO {
    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private Long classId;
    private Set<String> roles;
    private Set<String> permissions;
    private List<SysPermission> menus;
    private String dataScope;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public List<SysPermission> getMenus() { return menus; }
    public void setMenus(List<SysPermission> menus) { this.menus = menus; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}
