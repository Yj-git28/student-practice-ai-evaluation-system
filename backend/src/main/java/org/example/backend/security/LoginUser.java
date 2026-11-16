package org.example.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private Long classId;
    private String password;

    /** 角色码集合，如 ["TEACHER"]。对应 sys_role.role_code */
    private Set<String> roles = new HashSet<>();
    /** 权限码集合，如 ["training:list","training:publish"]。对应 sys_permission.perm_code */
    private Set<String> permissions = new HashSet<>();
    /** 数据范围：ALL / TEACHER_SELF / STUDENT_SELF，第 3.6 步用 */
    private String dataScope;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        // 角色统一加 ROLE_ 前缀，方便 hasRole('ADMIN')
        roles.forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r)));
        // 权限码原样放入，对应 hasAuthority('training:publish')
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority(p)));
        return list;
    }

    @Override public String getPassword()          { return password; }
    @Override public String getUsername()          { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked()  { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()           { return true; }  // 表里没有 status 字段，恒 true

    // 其余 getter/setter 省略
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public void setPassword(String password) { this.password = password; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}